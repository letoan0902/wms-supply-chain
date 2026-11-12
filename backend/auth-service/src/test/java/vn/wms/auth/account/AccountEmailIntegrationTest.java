package vn.wms.auth.account;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.account.service.AccountTokenService;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.AccountEmailKind;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kích hoạt doanh nghiệp qua email và đặt lại mật khẩu bằng liên kết dùng một lần (WMS-23.6).
 * Liên kết lấy từ sự kiện auth-service phát cho notification-service (bộ phát được giả lập trong lớp cha).
 */
class AccountEmailIntegrationTest extends BaseIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String NEW_PASSWORD = "MatKhauMoi2026";

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /** Doanh nghiệp vừa đăng ký trong test. */
    private record Registered(long companyId, String username, String email) {
    }

    private Registered register() throws Exception {
        String suffix = System.nanoTime() + "_" + SEQ.incrementAndGet();
        String username = "act_" + suffix;
        String email = username + "@wms.vn";
        RegisterCompanyRequest request = RegisterCompanyRequest.builder()
                .companyName("Công ty Kích Hoạt " + suffix)
                .fullName("Người Đại Diện " + suffix)
                .username(username)
                .email(email)
                .password(DEFAULT_PASSWORD)
                .build();
        String body = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.activationRequired").value(true))
                .andExpect(jsonPath("$.data.company.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(body).path("data").path("company").path("id").asLong();
        return new Registered(companyId, username, email);
    }

    private Registered registerAndActivate() throws Exception {
        Registered registered = register();
        activateCompanyByEmail(registered.email());
        return registered;
    }

    private ResultActions postJson(String path, Map<String, ?> body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, password)));
    }

    private Company company(long companyId) {
        entityManager.clear();
        return companyRepository.findById(companyId).orElseThrow();
    }

    private List<String> storedHashes(long companyId) {
        return jdbcTemplate.queryForList("SELECT token_hash FROM account_tokens WHERE company_id = ?", String.class,
                companyId);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------- Đăng ký và kích hoạt

    @Test
    @DisplayName("Đăng ký: doanh nghiệp PENDING, phát sự kiện kích hoạt đúng nội dung và doanh nghiệp, chỉ lưu bản băm")
    void registrationLeavesCompanyPendingAndPublishesActivationEvent() throws Exception {
        Instant before = Instant.now();
        Registered registered = register();

        Company company = company(registered.companyId());
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.PENDING);
        assertThat(company.getActivatedAt()).isNull();
        // 30 ngày dùng thử vẫn tính từ lúc đăng ký
        assertThat(subscriptionRepository.findByCompanyId(registered.companyId()).orElseThrow().getTrialEndsAt())
                .isBetween(before.truncatedTo(ChronoUnit.SECONDS).plus(30, ChronoUnit.DAYS),
                        Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(publishedEvents).hasSize(1);
        PublishedEvent published = publishedEvents.get(0);
        assertThat(published.routingKey()).isEqualTo(WmsEvents.RoutingKeys.AUTH_ACTIVATION_REQUESTED);
        assertThat(published.companyId()).isEqualTo(registered.companyId());
        AccountEmailRequestedEvent event = (AccountEmailRequestedEvent) published.payload();
        assertThat(event.kind()).isEqualTo(AccountEmailKind.ACTIVATION);
        assertThat(event.companyId()).isEqualTo(registered.companyId());
        assertThat(event.companyName()).isEqualTo(company.getName());
        assertThat(event.recipientEmail()).isEqualTo(registered.email());
        assertThat(event.recipientName()).startsWith("Người Đại Diện ");
        assertThat(event.link()).startsWith("http://localhost:5173/activate?token=");
        assertThat(event.expiresAt()).isBetween(before.plus(24, ChronoUnit.HOURS),
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertThat(event.toString()).doesNotContain(event.link());

        // Mã: ít nhất 32 byte ngẫu nhiên, Base64 URL-safe; cơ sở dữ liệu chỉ có SHA-256 của mã
        String token = latestTokenSentTo(registered.email(), AccountEmailKind.ACTIVATION);
        assertThat(token).matches("^[A-Za-z0-9_-]{43,}$");
        assertThat(storedHashes(registered.companyId()))
                .containsExactly(AccountTokenService.hash(token))
                .doesNotContain(token);

        login(registered.username(), DEFAULT_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")))
                .andExpect(jsonPath("$.data.companyStatus").value("PENDING"));
    }

    @Test
    @DisplayName("Bấm liên kết kích hoạt thì đăng nhập được; dùng lại liên kết nhận TOKEN_INVALID")
    void activationThenLoginWorksAndTokenIsSingleUse() throws Exception {
        Registered registered = register();
        Instant trialEndsAt = subscriptionRepository.findByCompanyId(registered.companyId()).orElseThrow()
                .getTrialEndsAt();
        String token = latestTokenSentTo(registered.email(), AccountEmailKind.ACTIVATION);

        postJson("/api/auth/companies/activate", Map.of("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.username").value(registered.username()))
                .andExpect(jsonPath("$.data.activatedAt").isNotEmpty());

        Company company = company(registered.companyId());
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getActivatedAt()).isNotNull();
        assertThat(subscriptionRepository.findByCompanyId(registered.companyId()).orElseThrow().getTrialEndsAt())
                .isEqualTo(trialEndsAt);

        login(registered.username(), DEFAULT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.id").value(registered.companyId()));

        postJson("/api/auth/companies/activate", Map.of("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Mã sai, mã quá 24 giờ, mã đặt lại mật khẩu dùng để kích hoạt: TOKEN_INVALID, doanh nghiệp vẫn PENDING")
    void wrongExpiredOrForeignTokensAreRejected() throws Exception {
        postJson("/api/auth/companies/activate", Map.of("token", "khong-phai-ma-that-0123456789abcdefghijklmnop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        postJson("/api/auth/companies/activate", Map.of("token", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Registered registered = register();
        String token = latestTokenSentTo(registered.email(), AccountEmailKind.ACTIVATION);
        jdbcTemplate.update("UPDATE account_tokens SET expires_at = ? WHERE company_id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), registered.companyId());
        postJson("/api/auth/companies/activate", Map.of("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(company(registered.companyId()).getStatus()).isEqualTo(CompanyStatus.PENDING);

        // Mã đặt lại mật khẩu không kích hoạt được doanh nghiệp, mã kích hoạt không đặt lại được mật khẩu
        Registered active = registerAndActivate();
        postJson("/api/auth/password/forgot", Map.of("email", active.email())).andExpect(status().isOk());
        String resetToken = latestTokenSentTo(active.email(), AccountEmailKind.PASSWORD_RESET);
        postJson("/api/auth/companies/activate", Map.of("token", resetToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        postJson("/api/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Làm mới token không cho doanh nghiệp đã về PENDING đi qua")
    void refreshIsRefusedWhileCompanyIsPending() throws Exception {
        Registered registered = registerAndActivate();
        String refreshToken = body(login(registered.username(), DEFAULT_PASSWORD).andExpect(status().isOk()))
                .path("data").path("refreshToken").asText();

        jdbcTemplate.update("UPDATE companies SET status = 'PENDING' WHERE id = ?", registered.companyId());
        entityManager.clear();

        postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
    }

    // ---------------------------------------------------------------- Gửi lại email kích hoạt

    @Test
    @DisplayName("Gửi lại kích hoạt: tối đa một lần mỗi phút cho mỗi email, liên kết mới thay liên kết cũ")
    void resendActivationIsThrottledAndReplacesOldLink() throws Exception {
        Registered registered = register();
        String firstToken = latestTokenSentTo(registered.email(), AccountEmailKind.ACTIVATION);

        JsonNode first = body(postJson("/api/auth/companies/resend-activation", Map.of("email", registered.email()))
                .andExpect(status().isOk()));
        assertThat(accountEmailsTo(registered.email(), AccountEmailKind.ACTIVATION)).hasSize(2);
        PublishedEvent resent = publishedEvents.get(publishedEvents.size() - 1);
        assertThat(resent.routingKey()).isEqualTo(WmsEvents.RoutingKeys.AUTH_ACTIVATION_REQUESTED);
        assertThat(resent.companyId()).isEqualTo(registered.companyId());

        // Lần thứ hai trong cùng phút (viết hoa khác cũng là cùng email): vẫn 200, không gửi thêm
        JsonNode second = body(postJson("/api/auth/companies/resend-activation",
                Map.of("email", registered.email().toUpperCase())).andExpect(status().isOk()));
        assertThat(second.path("message")).isEqualTo(first.path("message"));
        assertThat(accountEmailsTo(registered.email(), AccountEmailKind.ACTIVATION)).hasSize(2);
        assertThat(mockRedisStore.keySet()).anyMatch(key -> key.startsWith("wms:auth:mail-throttle:activation:"))
                .noneMatch(key -> key.contains(registered.email()));

        // Hết một phút (khóa giới hạn hết hạn): gửi được lần nữa
        mockRedisStore.keySet().removeIf(key -> key.startsWith("wms:auth:mail-throttle:activation:"));
        postJson("/api/auth/companies/resend-activation", Map.of("email", registered.email()))
                .andExpect(status().isOk());
        assertThat(accountEmailsTo(registered.email(), AccountEmailKind.ACTIVATION)).hasSize(3);

        // Chỉ liên kết mới nhất còn dùng được
        postJson("/api/auth/companies/activate", Map.of("token", firstToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        activateCompanyByEmail(registered.email());
    }

    @Test
    @DisplayName("Gửi lại kích hoạt cho email lạ hoặc doanh nghiệp đã hoạt động: cùng phản hồi 200, không phát sự kiện")
    void resendActivationRevealsNothing() throws Exception {
        JsonNode unknown = body(postJson("/api/auth/companies/resend-activation",
                Map.of("email", "khong.ton.tai@wms.vn")).andExpect(status().isOk()));
        JsonNode active = body(postJson("/api/auth/companies/resend-activation",
                Map.of("email", "admin@wms.vn")).andExpect(status().isOk()));

        assertThat(publishedEvents).isEmpty();
        assertThat(active.path("message")).isEqualTo(unknown.path("message"));
        assertThat(active.has("data")).isEqualTo(unknown.has("data"));

        postJson("/api/auth/companies/resend-activation", Map.of("email", "khong-phai-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------------------------------------------------------------- Quên và đặt lại mật khẩu

    @Test
    @DisplayName("Quên mật khẩu: nhận liên kết, đặt mật khẩu mới, mật khẩu cũ hết dùng được, refresh token bị vô hiệu")
    void forgotAndResetPasswordHappyPath() throws Exception {
        Registered registered = registerAndActivate();
        String oldRefreshToken = body(login(registered.username(), DEFAULT_PASSWORD).andExpect(status().isOk()))
                .path("data").path("refreshToken").asText();
        assertThat(mockRedisStore).containsKey("refresh:" + registered.username());
        publishedEvents.clear();

        Instant before = Instant.now();
        postJson("/api/auth/password/forgot", Map.of("email", registered.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(publishedEvents).hasSize(1);
        PublishedEvent published = publishedEvents.get(0);
        assertThat(published.routingKey()).isEqualTo(WmsEvents.RoutingKeys.AUTH_PASSWORD_RESET_REQUESTED);
        assertThat(published.companyId()).isEqualTo(registered.companyId());
        AccountEmailRequestedEvent event = (AccountEmailRequestedEvent) published.payload();
        assertThat(event.kind()).isEqualTo(AccountEmailKind.PASSWORD_RESET);
        assertThat(event.recipientEmail()).isEqualTo(registered.email());
        assertThat(event.link()).startsWith("http://localhost:5173/reset-password?token=");
        assertThat(event.expiresAt()).isBetween(before.plus(30, ChronoUnit.MINUTES),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        String token = latestTokenSentTo(registered.email(), AccountEmailKind.PASSWORD_RESET);
        assertThat(storedHashes(registered.companyId())).contains(AccountTokenService.hash(token)).doesNotContain(token);

        postJson("/api/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk());

        // Mọi refresh token của tài khoản bị vô hiệu
        assertThat(mockRedisStore).doesNotContainKey("refresh:" + registered.username());
        postJson("/api/auth/refresh", Map.of("refreshToken", oldRefreshToken))
                .andExpect(status().isUnauthorized());

        login(registered.username(), DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        login(registered.username(), NEW_PASSWORD).andExpect(status().isOk());
        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                userRepository.findByUsername(registered.username()).orElseThrow().getPasswordHash())).isTrue();

        // Liên kết chỉ dùng một lần
        postJson("/api/auth/password/reset", Map.of("token", token, "newPassword", "MatKhauKhac2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Quên mật khẩu với email lạ, tài khoản bị khóa, doanh nghiệp chưa kích hoạt: cùng 200, không phát sự kiện")
    void forgotPasswordRevealsNothing() throws Exception {
        Registered disabled = registerAndActivate();
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE username = ?", disabled.username());
        entityManager.clear();
        Registered pending = register();
        publishedEvents.clear();

        JsonNode unknown = body(postJson("/api/auth/password/forgot", Map.of("email", "ai.do@khong-co.vn"))
                .andExpect(status().isOk()));
        JsonNode ofDisabled = body(postJson("/api/auth/password/forgot", Map.of("email", disabled.email()))
                .andExpect(status().isOk()));
        JsonNode ofPending = body(postJson("/api/auth/password/forgot", Map.of("email", pending.email()))
                .andExpect(status().isOk()));

        assertThat(publishedEvents).isEmpty();
        assertThat(ofDisabled).isEqualTo(withSameTimestamp(unknown, ofDisabled));
        assertThat(ofPending).isEqualTo(withSameTimestamp(unknown, ofPending));
    }

    @Test
    @DisplayName("Quên mật khẩu: mỗi email tối đa một lần mỗi phút")
    void forgotPasswordIsThrottled() throws Exception {
        Registered registered = registerAndActivate();
        publishedEvents.clear();

        postJson("/api/auth/password/forgot", Map.of("email", registered.email())).andExpect(status().isOk());
        postJson("/api/auth/password/forgot", Map.of("email", registered.email())).andExpect(status().isOk());

        assertThat(accountEmailsTo(registered.email(), AccountEmailKind.PASSWORD_RESET)).hasSize(1);
    }

    @Test
    @DisplayName("Đặt lại mật khẩu: mật khẩu ngắn bị từ chối, mã sai hoặc quá 30 phút nhận TOKEN_INVALID")
    void resetPasswordValidation() throws Exception {
        Registered registered = registerAndActivate();
        postJson("/api/auth/password/forgot", Map.of("email", registered.email())).andExpect(status().isOk());
        String token = latestTokenSentTo(registered.email(), AccountEmailKind.PASSWORD_RESET);

        postJson("/api/auth/password/reset", Map.of("token", token, "newPassword", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        postJson("/api/auth/password/reset", Map.of("token", token + "x", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        jdbcTemplate.update("UPDATE account_tokens SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.SECONDS)), AccountTokenService.hash(token));
        postJson("/api/auth/password/reset", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // Mật khẩu không đổi khi mã không hợp lệ
        login(registered.username(), DEFAULT_PASSWORD).andExpect(status().isOk());
    }

    /** Bản sao của {@code expected} mang {@code timestamp} của {@code actual}, để so sánh phần còn lại của phản hồi. */
    private JsonNode withSameTimestamp(JsonNode expected, JsonNode actual) {
        com.fasterxml.jackson.databind.node.ObjectNode copy = expected.deepCopy();
        copy.set("timestamp", actual.get("timestamp"));
        return copy;
    }
}
