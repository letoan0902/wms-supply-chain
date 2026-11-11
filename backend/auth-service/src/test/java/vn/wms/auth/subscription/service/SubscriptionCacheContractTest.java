package vn.wms.auth.subscription.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.subscription.entity.Plan;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.SubscriptionCacheKeys;
import vn.wms.common.plan.SubscriptionSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hợp đồng giữa bên ghi và bên đọc bộ nhớ đệm thuê bao (docs/SAAS_SPEC.md mục 2.7): những gì
 * {@link SubscriptionCacheWriter} của auth-service ghi phải được {@link SubscriptionSnapshot#fromHash} của module
 * common (PlanLimitChecker) đọc lại đúng gói, trạng thái, mốc hết hạn, giới hạn và quyền API. api-gateway đọc cùng
 * tên khóa và tên trường nên test này cũng bảo vệ gateway khỏi việc đổi tên một phía.
 */
class SubscriptionCacheContractTest {

    private static final long COMPANY_ID = 42L;

    private RedisTemplate<String, String> redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private SubscriptionCacheWriter writer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        writer = new SubscriptionCacheWriter(redisTemplate);
    }

    private static Plan plan(String code, int users, int warehouses, int skus, int ai, boolean api) {
        return Plan.builder().code(code).name(code)
                .priceMonthly(BigDecimal.ZERO).priceYearly(BigDecimal.ZERO)
                .maxUsers(users).maxWarehouses(warehouses).maxSkus(skus).maxAiRequests(ai)
                .apiAccess(api).trialDays(0)
                .build();
    }

    private static Subscription subscription(Plan plan, SubscriptionStatus status, Instant trialEndsAt,
                                             Instant expiresAt) {
        Company company = new Company();
        company.setId(COMPANY_ID);
        return Subscription.builder()
                .company(company).plan(plan).status(status).trialEndsAt(trialEndsAt).expiresAt(expiresAt)
                .build();
    }

    /** Ghi qua writer (không có giao dịch nên ghi ngay) rồi đọc lại bằng bộ đọc của common. */
    @SuppressWarnings("unchecked")
    private SubscriptionSnapshot writeAndReadBack(Subscription subscription) {
        writer.write(subscription);
        ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(SubscriptionCacheKeys.subscriptionKey(COMPANY_ID)), fields.capture());
        return SubscriptionSnapshot.fromHash(fields.getValue()).orElseThrow();
    }

    @Test
    @DisplayName("Gói TRIAL đang chạy: mốc hết hạn là ngày hết dùng thử, giới hạn và quyền API đọc lại đúng")
    void trialSubscriptionRoundTrips() {
        Instant trialEndsAt = Instant.parse("2026-12-09T03:00:00Z");

        SubscriptionSnapshot snapshot = writeAndReadBack(subscription(
                plan("TRIAL", 50, 20, -1, 500, true), SubscriptionStatus.ACTIVE, trialEndsAt, null));

        assertThat(snapshot.plan()).isEqualTo("TRIAL");
        assertThat(snapshot.status()).isEqualTo(SubscriptionCacheKeys.STATUS_ACTIVE);
        assertThat(snapshot.isExpired()).isFalse();
        assertThat(snapshot.expiresAt()).isEqualTo(trialEndsAt);
        assertThat(snapshot.maxOf(PlanLimit.USERS)).isEqualTo(50);
        assertThat(snapshot.maxOf(PlanLimit.WAREHOUSES)).isEqualTo(20);
        assertThat(snapshot.maxOf(PlanLimit.SKUS)).isEqualTo(SubscriptionCacheKeys.UNLIMITED);
        assertThat(snapshot.maxOf(PlanLimit.AI_REQUESTS)).isEqualTo(500);
        assertThat(snapshot.apiAccess()).isTrue();
        assertThat(snapshot.maxOf(PlanLimit.API_ACCESS)).isEqualTo(SubscriptionCacheKeys.UNLIMITED);
    }

    @Test
    @DisplayName("Gói FREE: không có mốc hết hạn, giới hạn thấp và không được dùng API key")
    void freeSubscriptionRoundTrips() {
        SubscriptionSnapshot snapshot = writeAndReadBack(subscription(
                plan("FREE", 3, 1, 200, 20, false), SubscriptionStatus.ACTIVE, null, null));

        assertThat(snapshot.plan()).isEqualTo("FREE");
        assertThat(snapshot.expiresAt()).isNull();
        assertThat(snapshot.maxOf(PlanLimit.USERS)).isEqualTo(3);
        assertThat(snapshot.maxOf(PlanLimit.WAREHOUSES)).isEqualTo(1);
        assertThat(snapshot.maxOf(PlanLimit.SKUS)).isEqualTo(200);
        assertThat(snapshot.maxOf(PlanLimit.AI_REQUESTS)).isEqualTo(20);
        assertThat(snapshot.apiAccess()).isFalse();
        assertThat(snapshot.maxOf(PlanLimit.API_ACCESS)).isZero();
        assertThat(snapshot.planDisplayName()).isEqualTo("Khởi Đầu");
    }

    @Test
    @DisplayName("Gói PRO đã hết hạn: bên đọc thấy trạng thái EXPIRED và mốc expires_at")
    void expiredProSubscriptionRoundTrips() {
        Instant expiresAt = Instant.parse("2026-11-01T00:00:00Z");

        SubscriptionSnapshot snapshot = writeAndReadBack(subscription(
                plan("PRO", 50, 20, -1, 500, true), SubscriptionStatus.EXPIRED, null, expiresAt));

        assertThat(snapshot.plan()).isEqualTo("PRO");
        assertThat(snapshot.status()).isEqualTo(SubscriptionCacheKeys.STATUS_EXPIRED);
        assertThat(snapshot.isExpired()).isTrue();
        assertThat(snapshot.expiresAt()).isEqualTo(expiresAt);
        assertThat(snapshot.apiAccess()).isTrue();
    }

    @Test
    @DisplayName("Hằng số cũ của SubscriptionCacheWriter trùng với SubscriptionCacheKeys")
    void legacyConstantsAliasCommonKeys() {
        assertThat(SubscriptionCacheWriter.keyOf(COMPANY_ID)).isEqualTo("wms:subscription:42");
        assertThat(SubscriptionCacheWriter.KEY_PREFIX).isEqualTo(SubscriptionCacheKeys.SUBSCRIPTION_KEY_PREFIX);
        assertThat(SubscriptionCacheWriter.FIELD_MAX_USERS).isEqualTo(PlanLimit.USERS.maxField());
        assertThat(SubscriptionCacheWriter.FIELD_API_ACCESS).isEqualTo(PlanLimit.API_ACCESS.maxField());
    }
}
