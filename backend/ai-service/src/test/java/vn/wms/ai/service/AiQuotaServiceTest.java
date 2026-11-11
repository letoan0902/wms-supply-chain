package vn.wms.ai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.client.AiReply;
import vn.wms.ai.config.AiMode;
import vn.wms.ai.config.AiProperties;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.AiUsageResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.PlanLimitChecker;
import vn.wms.common.plan.PlanLimitViolation;
import vn.wms.common.tenant.TenantContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hạn mức lượt AI: dùng {@link PlanLimitChecker} thật, chỉ giả lập Redis (hash thuê bao do auth-service ghi và bộ
 * đếm {@code wms:usage:ai:{companyId}:{yyyyMM}}).
 */
class AiQuotaServiceTest {

    private static final long COMPANY_ID = 701L;
    private static final String AI_KEY_PREFIX = "wms:usage:ai:701:";
    private static final AiReply ANSWER = AiReply.answered("Trả lời", "end_turn", 10, 20, false);

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
        checker = new PlanLimitChecker(redisTemplate);
        TenantContext.setCompanyId(COMPANY_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AiQuotaService service(AiMode mode, AiProperties properties) {
        @SuppressWarnings("unchecked")
        ObjectProvider<PlanLimitChecker> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(checker);
        Clock clock = Clock.fixed(Instant.parse("2026-11-30T20:00:00Z"), ZoneOffset.UTC);
        return new AiQuotaService(provider, new AiRuntime(mode, properties.model()), properties, clock);
    }

    private AiQuotaService liveService() {
        return service(AiMode.LIVE, AiTestSupport.properties(false, true));
    }

    private void givenPlan(String plan, String maxAiRequests, String used) {
        when(hashOperations.entries("wms:subscription:" + COMPANY_ID))
                .thenReturn(Map.copyOf(AiTestSupport.subscription(plan, maxAiRequests)));
        when(valueOperations.get(startsWith(AI_KEY_PREFIX))).thenReturn(used);
    }

    @Test
    void underQuota_isAllowed() {
        givenPlan("FREE", "20", "19");

        assertThatCode(() -> liveService().checkQuota()).doesNotThrowAnyException();
    }

    @Test
    void atQuota_isRejectedWithPlanLimitExceeded() {
        givenPlan("FREE", "20", "20");

        assertThatThrownBy(() -> liveService().checkQuota())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED);
                    assertThat(ex.getMessage()).isEqualTo(
                            "Gói Khởi Đầu chỉ cho phép tối đa 20 lượt AI mỗi tháng. Nâng cấp gói để dùng thêm lượt AI.");
                    assertThat(ex.getData()).isEqualTo(new PlanLimitViolation(PlanLimit.AI_REQUESTS, 20, 20, "FREE"));
                });
    }

    @Test
    void unlimitedPlan_isAllowed() {
        givenPlan("PRO", "-1", "100000");

        assertThatCode(() -> liveService().checkQuota()).doesNotThrowAnyException();
    }

    @Test
    void missingSubscriptionCache_failsOpen() {
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        when(valueOperations.get(anyString())).thenReturn("999");

        assertThatCode(() -> liveService().checkQuota()).doesNotThrowAnyException();
    }

    @Test
    void redisDown_failsOpen() {
        when(hashOperations.entries(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatCode(() -> liveService().checkQuota()).doesNotThrowAnyException();
    }

    @Test
    void withoutCompany_isRejectedWithCompanyRequired() {
        TenantContext.clear();

        assertThatThrownBy(() -> liveService().checkQuota())
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_REQUIRED));
    }

    @Test
    void success_incrementsMonthlyCounter() {
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(6L);

        assertThat(liveService().recordSuccess(ANSWER)).isEqualTo(6L);
        verify(valueOperations).increment(startsWith(AI_KEY_PREFIX));
    }

    @Test
    void refusal_isNotCounted() {
        when(valueOperations.get(startsWith(AI_KEY_PREFIX))).thenReturn("5");

        long used = liveService().recordSuccess(AiReply.refusal("refusal", 30, 0));

        assertThat(used).isEqualTo(5L);
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void mockAnswer_isCountedByDefault() {
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(1L);

        long used = service(AiMode.MOCK, AiTestSupport.properties(true, true)).recordSuccess(ANSWER);

        assertThat(used).isEqualTo(1L);
        verify(redisTemplate).expire(startsWith(AI_KEY_PREFIX), any());
    }

    @Test
    void mockAnswer_isNotCountedWhenDisabled() {
        long used = service(AiMode.MOCK, AiTestSupport.properties(true, false)).recordSuccess(ANSWER);

        assertThat(used).isZero();
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void usage_reportsUsedMaxMonthAndPlan() {
        givenPlan("TRIAL", "500", "42");

        AiUsageResponse usage = liveService().usage();

        // 20:00 UTC ngày 30/11 là 03:00 ngày 01/12 giờ Việt Nam
        assertThat(usage).isEqualTo(new AiUsageResponse(42, 500L, "2026-12", "TRIAL"));
    }

    @Test
    void usage_unlimitedPlanReportsMinusOne() {
        givenPlan("PRO", "-1", null);

        assertThat(liveService().usage()).isEqualTo(new AiUsageResponse(0, -1L, "2026-12", "PRO"));
    }

    @Test
    void usage_missingSubscriptionCacheReturnsNullMaxAndPlan() {
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        when(valueOperations.get(startsWith(AI_KEY_PREFIX))).thenReturn("3");

        assertThat(liveService().usage()).isEqualTo(new AiUsageResponse(3, null, "2026-12", null));
    }
}
