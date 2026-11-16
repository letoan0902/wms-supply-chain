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
import vn.wms.ai.client.AiClient;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        return new AiQuotaService(provider, redisTemplate, new AiRuntime(mode, properties.model()), properties, clock);
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

        assertThatCode(() -> liveService().reserve()).doesNotThrowAnyException();
    }

    @Test
    void atQuota_isRejectedWithPlanLimitExceeded() {
        givenPlan("FREE", "20", "20");

        assertThatThrownBy(() -> liveService().reserve())
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

        assertThatCode(() -> liveService().reserve()).doesNotThrowAnyException();
    }

    @Test
    void missingSubscriptionCache_failsOpen() {
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        when(valueOperations.get(anyString())).thenReturn("999");

        assertThatCode(() -> liveService().reserve()).doesNotThrowAnyException();
    }

    @Test
    void redisDown_failsOpen() {
        when(hashOperations.entries(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatCode(() -> liveService().reserve()).doesNotThrowAnyException();
    }

    @Test
    void withoutCompany_isRejectedWithCompanyRequired() {
        TenantContext.clear();

        assertThatThrownBy(() -> liveService().reserve())
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_REQUIRED));
    }

    @Test
    void confirmedTurn_keepsTheReservedIncrement() {
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(6L);

        try (AiQuotaService.Reservation turn = liveService().reserve()) {
            assertThat(turn.confirm(false)).isEqualTo(6L);
        }
        verify(valueOperations).increment(startsWith(AI_KEY_PREFIX));
        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    void reserveAtLastTurnOverLimit_releasesAndRejectsWithSameViolation() {
        // Bước đọc thấy 19 (còn lượt) nhưng một yêu cầu khác đã giữ lượt cuối: INCR trả 21.
        givenPlan("FREE", "20", "19");
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(21L);

        assertThatThrownBy(() -> liveService().reserve())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED);
                    assertThat(ex.getData()).isEqualTo(new PlanLimitViolation(PlanLimit.AI_REQUESTS, 20, 20, "FREE"));
                });
        verify(valueOperations).decrement(startsWith(AI_KEY_PREFIX));
    }

    @Test
    void failedCall_releasesTheTurnOnClose() {
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(1L);

        assertThatThrownBy(() -> {
            try (AiQuotaService.Reservation ignored = liveService().reserve()) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI không phản hồi");
            }
        }).isInstanceOf(ApiException.class);
        verify(valueOperations).decrement(startsWith(AI_KEY_PREFIX));
        verify(redisTemplate).expire(startsWith(AI_KEY_PREFIX), any());
    }

    @Test
    void refusal_isNotCounted() {
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(6L);
        when(valueOperations.get(startsWith(AI_KEY_PREFIX))).thenReturn("5");

        long used;
        try (AiQuotaService.Reservation turn = liveService().reserve()) {
            used = turn.confirm(true);
        }

        assertThat(used).isEqualTo(5L);
        verify(valueOperations).decrement(startsWith(AI_KEY_PREFIX));
    }

    @Test
    void mockAnswer_isCountedByDefault() {
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(1L);

        try (AiQuotaService.Reservation turn = service(AiMode.MOCK, AiTestSupport.properties(true, true)).reserve()) {
            assertThat(turn.confirm()).isEqualTo(1L);
        }
        verify(redisTemplate).expire(startsWith(AI_KEY_PREFIX), any());
        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    void mockAnswer_isNotCountedWhenDisabled() {
        try (AiQuotaService.Reservation turn = service(AiMode.MOCK, AiTestSupport.properties(true, false)).reserve()) {
            assertThat(turn.confirm()).isZero();
        }
        verify(valueOperations, never()).increment(anyString());
        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    void redisDownWhileReserving_failsOpenAndReleasesNothing() {
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));

        try (AiQuotaService.Reservation turn = liveService().reserve()) {
            assertThat(turn.confirm()).isZero();
        }
        verify(valueOperations, never()).decrement(anyString());
    }

    /**
     * Doanh nghiệp đang ở 19/20 lượt gửi 5 câu hỏi cùng lúc: chỉ 1 câu được gọi mô hình, 4 câu còn lại nhận
     * {@code PLAN_LIMIT_EXCEEDED}, bộ đếm dừng ở 20. Bộ đếm Redis giả lập bằng {@link AtomicLong} (INCR, DECR nguyên tử).
     */
    @Test
    void parallelRequestsAtLastTurn_onlyOneIsAnsweredAndCounterStopsAtLimit() throws Exception {
        AtomicLong counter = new AtomicLong(19);
        when(hashOperations.entries("wms:subscription:" + COMPANY_ID))
                .thenReturn(Map.copyOf(AiTestSupport.subscription("FREE", "20")));
        when(valueOperations.get(startsWith(AI_KEY_PREFIX))).thenAnswer(inv -> String.valueOf(counter.get()));
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenAnswer(inv -> counter.incrementAndGet());
        when(valueOperations.decrement(startsWith(AI_KEY_PREFIX))).thenAnswer(inv -> counter.decrementAndGet());

        int parallel = 5;
        CountDownLatch allCalling = new CountDownLatch(parallel);
        AiClient aiClient = mock(AiClient.class);
        // Mô hình chờ tới khi đủ 5 lời gọi (hoặc 2 giây): mã cũ để cả 5 câu qua bước kiểm tra trước khi tăng bộ đếm.
        when(aiClient.ask(any())).thenAnswer(inv -> {
            allCalling.countDown();
            allCalling.await(2, TimeUnit.SECONDS);
            return ANSWER;
        });
        AiAskService askService = new AiAskService(aiClient, liveService(), AiTestSupport.properties(false, true));

        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        AtomicInteger answered = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                futures.add(pool.submit(() -> {
                    TenantContext.setCompanyId(COMPANY_ID);
                    try {
                        askService.ask("Câu hỏi");
                        answered.incrementAndGet();
                    } catch (ApiException ex) {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED);
                        rejected.incrementAndGet();
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(answered.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(4);
        assertThat(counter.get()).isEqualTo(20L);
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
