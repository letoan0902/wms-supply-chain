package vn.wms.auth.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.auth.subscription.service.SubscriptionCacheWriter;
import vn.wms.auth.subscription.service.SubscriptionScheduler;
import vn.wms.auth.subscription.service.SubscriptionService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gói đã thanh toán không bị mất vì một thao tác hết hạn hoặc gia hạn chạy song song (WMS-23).
 *
 * <p>Giao dịch "webhook PayOS đang chạy" được giả lập bằng một kết nối JDBC riêng: khóa dòng thuê bao, chuyển sang
 * PRO và chỉ commit khi thao tác đang kiểm tra đã phải chờ khóa dòng đó (đọc từ {@code INFORMATION_SCHEMA.SESSIONS}
 * của H2), nên thứ tự xen kẽ luôn giống nhau ở mọi lần chạy.
 */
class SubscriptionConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionScheduler subscriptionScheduler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    /** Đăng ký và kích hoạt một doanh nghiệp mới, trả id doanh nghiệp (thuê bao TRIAL 30 ngày). */
    private long registerActiveCompany() throws Exception {
        String username = "conc_" + System.nanoTime() + "_" + SEQ.incrementAndGet();
        String email = username + "@wms.vn";
        String body = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterCompanyRequest.builder()
                                .companyName("Công ty Song Song " + username)
                                .fullName("Người Đại Diện " + username)
                                .username(username)
                                .email(email)
                                .password(DEFAULT_PASSWORD)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        activateCompanyByEmail(email);
        return objectMapper.readTree(body).path("data").path("company").path("id").asLong();
    }

    private void expireTrial(long companyId) {
        jdbcTemplate.update("UPDATE subscriptions SET trial_ends_at = ? WHERE company_id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), companyId);
    }

    private Subscription reload(long companyId) {
        entityManager.clear();
        return subscriptionRepository.findByCompanyId(companyId).orElseThrow();
    }

    /**
     * Chạy {@code action} ở luồng khác trong lúc một giao dịch khác giữ khóa dòng thuê bao và đã chuyển nó sang PRO
     * hết hạn ở {@code paidUntil}; commit giao dịch đó ngay khi {@code action} phải chờ khóa (hoặc đã chạy xong).
     */
    private void runWhilePaymentCommits(long companyId, Instant paidUntil, Runnable action) throws Exception {
        try (Connection payment = dataSource.getConnection()) {
            payment.setAutoCommit(false);
            try (PreparedStatement lock = payment.prepareStatement(
                    "SELECT id FROM subscriptions WHERE company_id = ? FOR UPDATE")) {
                lock.setLong(1, companyId);
                lock.executeQuery().close();
            }
            try (PreparedStatement update = payment.prepareStatement("UPDATE subscriptions SET plan_code = 'PRO', "
                    + "status = 'ACTIVE', trial_ends_at = NULL, expires_at = ? WHERE company_id = ?")) {
                update.setTimestamp(1, Timestamp.from(paidUntil));
                update.setLong(2, companyId);
                update.executeUpdate();
            }

            CompletableFuture<Void> running = CompletableFuture.runAsync(action);
            long deadline = System.currentTimeMillis() + 15_000;
            while (!running.isDone() && System.currentTimeMillis() < deadline && !someSessionIsBlocked()) {
                Thread.sleep(10);
            }
            payment.commit();
            running.get(30, TimeUnit.SECONDS);
        }
    }

    private boolean someSessionIsBlocked() {
        Integer blocked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL", Integer.class);
        return blocked != null && blocked > 0;
    }

    @Test
    @DisplayName("Kiểm tra hết hạn trên bản đọc cũ không ghi đè gói PRO vừa được thanh toán và commit")
    void expireOnStaleReadDoesNotOverwritePaidPlan() throws Exception {
        long companyId = registerActiveCompany();
        expireTrial(companyId);

        TransactionTemplate request = new TransactionTemplate(transactionManager);
        TransactionTemplate webhook = new TransactionTemplate(transactionManager);
        webhook.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Một yêu cầu (đăng nhập, xem thuê bao, tạo API key) đọc thuê bao TRIAL đã quá hạn; trước khi nó quyết định,
        // webhook PayOS kích hoạt PRO và commit trong giao dịch riêng
        Boolean expired = request.execute(tx -> {
            Subscription stale = subscriptionRepository.findByCompanyId(companyId).orElseThrow();
            assertThat(stale.getPlan().getCode()).isEqualTo("TRIAL");
            webhook.executeWithoutResult(inner -> subscriptionService.activate(companyId, "PRO", BillingCycle.MONTHLY));
            return subscriptionService.expireIfDue(stale);
        });

        assertThat(expired).isFalse();
        Subscription after = reload(companyId);
        assertThat(after.getPlan().getCode()).isEqualTo("PRO");
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(after.getExpiresAt()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("plan", "PRO")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("Tác vụ định kỳ không chuyển EXPIRED và không ghi Redis bản cũ khi gói PRO commit trong lúc nó chạy")
    void schedulerDoesNotOverwritePaymentCommittedDuringScan() throws Exception {
        long companyId = registerActiveCompany();
        expireTrial(companyId);
        mockRedisHashMap.clear();
        Instant paidUntil = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(30, ChronoUnit.DAYS);

        runWhilePaymentCommits(companyId, paidUntil, () -> subscriptionScheduler.scanAndExpireSubscriptions());

        Subscription after = reload(companyId);
        assertThat(after.getPlan().getCode()).isEqualTo("PRO");
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(after.getExpiresAt()).isEqualTo(paidUntil);
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("plan", "PRO")
                .containsEntry("status", "ACTIVE")
                .containsEntry("expiresAt", paidUntil.toString());
    }

    @Test
    @DisplayName("Hai lần thanh toán cùng lúc: lần sau chờ khóa dòng và cộng tiếp từ mốc hết hạn lần trước vừa ghi")
    void concurrentActivationsBothExtendExpiry() throws Exception {
        long companyId = registerActiveCompany();
        Instant trialEndsAt = reload(companyId).getTrialEndsAt();
        Instant firstPaidUntil = trialEndsAt.plus(30, ChronoUnit.DAYS);

        runWhilePaymentCommits(companyId, firstPaidUntil,
                () -> subscriptionService.activate(companyId, "PRO", BillingCycle.MONTHLY));

        Subscription after = reload(companyId);
        assertThat(after.getPlan().getCode()).isEqualTo("PRO");
        assertThat(after.getExpiresAt()).isEqualTo(firstPaidUntil.plus(30, ChronoUnit.DAYS));
    }
}
