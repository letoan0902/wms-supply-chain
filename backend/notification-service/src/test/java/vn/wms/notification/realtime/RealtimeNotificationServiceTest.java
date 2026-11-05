package vn.wms.notification.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Thứ tự đẩy thông báo chính rồi snapshot KPI, và hành vi khi KPI không đếm được. */
class RealtimeNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");

    private final SimpMessageSendingOperations messagingTemplate = mock(SimpMessageSendingOperations.class);
    private final RealtimeKpiService kpiService = mock(RealtimeKpiService.class);
    private final RealtimeNotificationService service = new RealtimeNotificationService(messagingTemplate, kpiService,
            new RealtimeNotificationFactory(Clock.fixed(NOW, ZoneOffset.UTC)));

    private final RealtimeNotification lowStock = new RealtimeNotification("msg-1", RealtimeEventType.LOW_STOCK,
            NotificationSeverity.WARNING, "Cảnh báo tồn kho thấp", "SKU-001 còn 5", NOW, null);

    @Test
    void publishesNotificationThenKpiSnapshot() {
        KpiSnapshot snapshot = new KpiSnapshot(LocalDate.parse("2026-11-05"), 0, 0, 0, 1, 0, NOW);
        when(kpiService.increment(KpiMetric.LOW_STOCK_ALERTS, "msg-1")).thenReturn(Optional.of(snapshot));

        service.dispatch(lowStock, KpiMetric.LOW_STOCK_ALERTS, "msg-1");

        verify(messagingTemplate).convertAndSend("/topic/alerts", (Object) lowStock);
        verify(messagingTemplate).convertAndSend(eq("/topic/kpi"), argThat((Object payload) ->
                payload instanceof RealtimeNotification kpi
                        && kpi.type() == RealtimeEventType.KPI_UPDATED && kpi.payload() == snapshot));
    }

    @Test
    void skipsKpiPushWhenNotCounted() {
        when(kpiService.increment(KpiMetric.LOW_STOCK_ALERTS, "msg-1")).thenReturn(Optional.empty());

        service.dispatch(lowStock, KpiMetric.LOW_STOCK_ALERTS, "msg-1");

        verify(messagingTemplate).convertAndSend("/topic/alerts", (Object) lowStock);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/kpi"), any(Object.class));
    }

    @Test
    void eventWithoutMetricDoesNotTouchKpi() {
        service.dispatch(lowStock, null, "msg-1");

        verify(messagingTemplate).convertAndSend("/topic/alerts", (Object) lowStock);
        verifyNoInteractions(kpiService);
    }

    @Test
    void brokerChannelErrorIsLoggedNotThrown() {
        doThrow(new MessageDeliveryException("kênh broker đầy"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> service.publish(lowStock)).doesNotThrowAnyException();
    }
}
