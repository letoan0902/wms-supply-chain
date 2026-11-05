package vn.wms.notification.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Bộ đếm KPI trên Redis (mock): chốt ngày theo giờ Việt Nam, TTL, chống đếm trùng và chịu lỗi Redis. */
class RealtimeKpiServiceTest {

    /** 18:30 UTC ngày 05/11 là 01:30 ngày 06/11 giờ Việt Nam. */
    private static final Instant NOW = Instant.parse("2026-11-05T18:30:00Z");
    private static final String TODAY_KEY = "wms:notification:kpi:2026-11-06";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
    private final RealtimeKpiService service = new RealtimeKpiService(redis, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
    }

    @Test
    void incrementsCounterOfVietnamDayAndReturnsSnapshot() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(hashOps.entries(TODAY_KEY)).thenReturn(Map.of(
                "purchaseOrders", "3", "salesOrders", "5", "dispatchedOrders", "2", "lowStockAlerts", "1",
                "inventoryMovements", "12", "updatedAt", NOW.toString()));

        Optional<KpiSnapshot> snapshot = service.increment(KpiMetric.PURCHASE_ORDERS, "msg-1");

        verify(valueOps).setIfAbsent("wms:notification:kpi:processed:msg-1", "1", Duration.ofDays(1));
        verify(hashOps).increment(TODAY_KEY, "purchaseOrders", 1);
        verify(hashOps).put(TODAY_KEY, "updatedAt", NOW.toString());
        verify(redis).expire(TODAY_KEY, Duration.ofDays(2));
        assertThat(snapshot).contains(new KpiSnapshot(LocalDate.parse("2026-11-06"), 3, 5, 2, 1, 12, NOW));
    }

    @Test
    void duplicateMessageIdIsNotCountedTwice() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        Optional<KpiSnapshot> snapshot = service.increment(KpiMetric.SALES_ORDERS, "msg-1");

        assertThat(snapshot).isEmpty();
        verify(hashOps, never()).increment(anyString(), any(), eq(1L));
    }

    @Test
    void eventWithoutMessageIdIsAlwaysCounted() {
        when(hashOps.entries(TODAY_KEY)).thenReturn(Map.of("inventoryMovements", "1"));

        Optional<KpiSnapshot> snapshot = service.increment(KpiMetric.INVENTORY_MOVEMENTS, null);

        verifyNoInteractions(valueOps);
        verify(hashOps).increment(TODAY_KEY, "inventoryMovements", 1);
        assertThat(snapshot).hasValueSatisfying(value -> {
            assertThat(value.inventoryMovements()).isEqualTo(1);
            assertThat(value.purchaseOrders()).isZero();
            assertThat(value.updatedAt()).isNull();
        });
    }

    @Test
    void redisFailureIsSwallowedSoConsumerKeepsWorking() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Redis không phản hồi"));

        assertThat(service.increment(KpiMetric.LOW_STOCK_ALERTS, "msg-9")).isEmpty();
    }

    @Test
    void currentSnapshotIsEmptyWhenNoEventYetAndPropagatesRedisErrors() {
        when(hashOps.entries(TODAY_KEY)).thenReturn(Map.of());

        assertThat(service.currentSnapshot()).isEqualTo(KpiSnapshot.empty(LocalDate.parse("2026-11-06")));

        when(hashOps.entries(TODAY_KEY)).thenThrow(new RedisConnectionFailureException("Redis không phản hồi"));
        assertThatThrownBy(service::currentSnapshot).isInstanceOf(RedisConnectionFailureException.class);
    }
}
