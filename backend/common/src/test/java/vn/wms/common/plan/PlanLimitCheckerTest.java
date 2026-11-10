package vn.wms.common.plan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlanLimitCheckerTest {

    private static final long COMPANY_ID = 7L;
    private static final String SUBSCRIPTION_KEY = "wms:subscription:7";
    private static final String USAGE_KEY = "wms:usage:7";
    /** 30/11/2026 18:30 UTC là 01:30 ngày 01/12/2026 giờ Việt Nam: bộ đếm AI phải thuộc tháng 12. */
    private static final Instant MONTH_BOUNDARY = Instant.parse("2026-11-30T18:30:00Z");

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private ValueOperations<String, String> valueOperations;
    private PlanLimitChecker checker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        checker = new PlanLimitChecker(redisTemplate, Clock.fixed(MONTH_BOUNDARY, ZoneOffset.UTC));
        TenantContext.setCompanyId(COMPANY_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenSubscription(String plan, String maxWarehouses, String maxSkus, String apiAccess) {
        Map<Object, Object> hash = new HashMap<>();
        hash.put("plan", plan);
        hash.put("status", "ACTIVE");
        hash.put("expiresAt", "");
        hash.put("maxUsers", "3");
        hash.put("maxWarehouses", maxWarehouses);
        hash.put("maxSkus", maxSkus);
        hash.put("maxAiRequests", "20");
        hash.put("apiAccess", apiAccess);
        when(hashOperations.entries(SUBSCRIPTION_KEY)).thenReturn(hash);
    }

    private void givenFreePlan() {
        givenSubscription("FREE", "1", "200", "false");
    }

    @Test
    void assertCanCreate_whenUnderLimit_allowsAndRecordsUsage() {
        givenFreePlan();

        assertThatCode(() -> checker.assertCanCreate(PlanLimit.SKUS, 199)).doesNotThrowAnyException();

        verify(hashOperations).put(USAGE_KEY, "skus", "199");
    }

    @Test
    void assertCanCreate_whenAtLimit_throwsPlanLimitExceededWithPayload() {
        givenFreePlan();

        assertThatThrownBy(() -> checker.assertCanCreate(PlanLimit.WAREHOUSES, 1))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED);
                    assertThat(ex.getHttpStatus()).isEqualTo(403);
                    assertThat(ex.getMessage())
                            .isEqualTo("Gói Khởi Đầu chỉ cho phép tối đa 1 kho. Nâng cấp gói để thêm kho mới.");
                    assertThat(ex.getData()).isEqualTo(new PlanLimitViolation(PlanLimit.WAREHOUSES, 1, 1, "FREE"));
                });
        verify(hashOperations).put(USAGE_KEY, "warehouses", "1");
    }

    @Test
    void assertCanCreate_whenAboveLimitAfterDowngrade_stillBlocks() {
        givenFreePlan();

        assertThatThrownBy(() -> checker.assertCanCreate(PlanLimit.SKUS, 350))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    PlanLimitViolation data = (PlanLimitViolation) ex.getData();
                    assertThat(data.current()).isEqualTo(350);
                    assertThat(data.max()).isEqualTo(200);
                    assertThat(ex.getMessage()).contains("tối đa 200 SKU");
                });
    }

    @Test
    void assertCanCreate_whenUnlimited_allowsAnyCount() {
        givenSubscription("TRIAL", "20", "-1", "true");

        assertThatCode(() -> checker.assertCanCreate(PlanLimit.SKUS, 1_000_000)).doesNotThrowAnyException();
    }

    @Test
    void assertCanCreate_whenSubscriptionHashMissing_failsOpen() {
        when(hashOperations.entries(SUBSCRIPTION_KEY)).thenReturn(Map.of());

        assertThatCode(() -> checker.assertCanCreate(PlanLimit.WAREHOUSES, 99)).doesNotThrowAnyException();
    }

    @Test
    void assertCanCreate_whenRedisUnreachable_failsOpen() {
        when(redisTemplate.opsForHash()).thenThrow(new RedisConnectionFailureException("Redis không chạy"));

        assertThatCode(() -> checker.assertCanCreate(PlanLimit.WAREHOUSES, 99)).doesNotThrowAnyException();
    }

    @Test
    void assertCanCreate_withoutCompany_throwsCompanyRequired() {
        TenantContext.clear();

        assertThatThrownBy(() -> checker.assertCanCreate(PlanLimit.WAREHOUSES, 0))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_REQUIRED));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void assertCanCreate_apiAccessDisabled_blocksWithDedicatedMessage() {
        givenFreePlan();

        assertThatThrownBy(() -> checker.assertCanCreate(PlanLimit.API_ACCESS, 0))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getData()).isEqualTo(new PlanLimitViolation(PlanLimit.API_ACCESS, 0, 0, "FREE"));
                    assertThat(ex.getMessage()).contains("không hỗ trợ API key");
                });
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    void assertCanCreateIfTenant_withoutCompany_skipsCountingAndRedis() {
        TenantContext.clear();

        checker.assertCanCreateIfTenant(PlanLimit.SKUS, () -> {
            throw new AssertionError("Không được đếm khi chưa có doanh nghiệp");
        });

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void assertCanCreateIfTenant_withCompany_enforcesLimit() {
        givenFreePlan();

        assertThatThrownBy(() -> checker.assertCanCreateIfTenant(PlanLimit.SKUS, () -> 200L))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void reportUsage_writesUsageHashField() {
        checker.reportUsage(PlanLimit.USERS, 2);
        checker.reportUsage(PlanLimit.WAREHOUSES, 1);

        verify(hashOperations).put(USAGE_KEY, "users", "2");
        verify(hashOperations).put(USAGE_KEY, "warehouses", "1");
    }

    @Test
    void reportUsage_withoutCompanyOrWithRedisError_doesNotThrow() {
        TenantContext.clear();
        checker.reportUsage(PlanLimit.SKUS, 5);
        verifyNoInteractions(redisTemplate);

        TenantContext.setCompanyId(COMPANY_ID);
        when(redisTemplate.opsForHash()).thenThrow(new RedisConnectionFailureException("Redis không chạy"));
        assertThatCode(() -> checker.reportUsage(PlanLimit.SKUS, 5)).doesNotThrowAnyException();
    }

    @Test
    void reportUsage_forLimitWithoutUsageField_isRejected() {
        assertThatThrownBy(() -> checker.reportUsage(PlanLimit.AI_REQUESTS, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incrementMonthlyAiUsage_usesVietnamMonthKeyAndSetsExpiryOnFirstUse() {
        when(valueOperations.increment("wms:usage:ai:7:202612")).thenReturn(1L);

        assertThat(checker.incrementMonthlyAiUsage()).isEqualTo(1L);

        verify(redisTemplate).expire("wms:usage:ai:7:202612", PlanLimitChecker.AI_USAGE_TTL);
    }

    @Test
    void incrementMonthlyAiUsage_doesNotResetExpiryAfterFirstUse() {
        when(valueOperations.increment("wms:usage:ai:7:202612")).thenReturn(5L);

        assertThat(checker.incrementMonthlyAiUsage()).isEqualTo(5L);

        verify(redisTemplate, never()).expire(anyString(), any(java.time.Duration.class));
    }

    @Test
    void assertAiQuota_whenMonthlyQuotaUsedUp_throws() {
        givenFreePlan();
        when(valueOperations.get("wms:usage:ai:7:202612")).thenReturn("20");

        assertThatThrownBy(() -> checker.assertAiQuota())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getData()).isEqualTo(new PlanLimitViolation(PlanLimit.AI_REQUESTS, 20, 20, "FREE"));
                    assertThat(ex.getMessage()).contains("tối đa 20 lượt AI mỗi tháng");
                });
    }

    @Test
    void assertAiQuota_whenUnderQuotaOrNoCounter_allows() {
        givenFreePlan();
        when(valueOperations.get("wms:usage:ai:7:202612")).thenReturn(null);

        assertThatCode(() -> checker.assertAiQuota()).doesNotThrowAnyException();
        assertThat(checker.currentMonthlyAiUsage()).isZero();
    }

    @Test
    void snapshot_parsesFieldsAndTreatsInvalidLimitsAsUnlimited() {
        Map<String, String> hash = Map.of(
                "plan", "PRO", "status", "EXPIRED", "expiresAt", "2026-12-09T03:00:00Z",
                "maxUsers", "50", "maxWarehouses", "abc", "apiAccess", "true");

        SubscriptionSnapshot snapshot = SubscriptionSnapshot.fromHash(hash).orElseThrow();

        assertThat(snapshot.isExpired()).isTrue();
        assertThat(snapshot.expiresAt()).isEqualTo(Instant.parse("2026-12-09T03:00:00Z"));
        assertThat(snapshot.maxOf(PlanLimit.USERS)).isEqualTo(50);
        assertThat(snapshot.maxOf(PlanLimit.WAREHOUSES)).isEqualTo(-1);
        assertThat(snapshot.maxOf(PlanLimit.SKUS)).isEqualTo(-1);
        assertThat(snapshot.maxOf(PlanLimit.API_ACCESS)).isEqualTo(-1);
        assertThat(snapshot.planDisplayName()).isEqualTo("Chuyên Nghiệp");
        assertThat(SubscriptionSnapshot.fromHash(Map.of("status", "ACTIVE"))).isEmpty();
    }
}
