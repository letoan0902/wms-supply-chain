package vn.wms.notification.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

/** Thứ tự đẩy thông báo chính rồi snapshot KPI lên kênh của doanh nghiệp, và hành vi khi KPI không đếm được. */
class RealtimeNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");
    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    private final SimpMessageSendingOperations messagingTemplate = mock(SimpMessageSendingOperations.class);
    private final RealtimeKpiService kpiService = mock(RealtimeKpiService.class);
    private final RealtimeNotificationService service = new RealtimeNotificationService(messagingTemplate, kpiService,
            new RealtimeNotificationFactory(Clock.fixed(NOW, ZoneOffset.UTC)));

    private final RealtimeNotification lowStock = new RealtimeNotification("msg-1", RealtimeEventType.LOW_STOCK,
            NotificationSeverity.WARNING, "Cảnh báo tồn kho thấp", "SKU-001 còn 5", NOW, null);

    @Test
    void publishesNotificationThenKpiSnapshot() {
        KpiSnapshot snapshot = new KpiSnapshot(LocalDate.parse("2026-11-05"), 0, 0, 0, 1, 0, NOW);
        when(kpiService.increment(COMPANY_A, KpiMetric.LOW_STOCK_ALERTS, "msg-1")).thenReturn(Optional.of(snapshot));

        service.dispatch(COMPANY_A, lowStock, KpiMetric.LOW_STOCK_ALERTS, "msg-1");

        verify(messagingTemplate).convertAndSend("/topic/company/1/alerts", (Object) lowStock);
        verify(messagingTemplate).convertAndSend(eq("/topic/company/1/kpi"), argThat((Object payload) ->
                payload instanceof RealtimeNotification kpi
                        && kpi.type() == RealtimeEventType.KPI_UPDATED && kpi.payload() == snapshot));
    }

    @Test
    void skipsKpiPushWhenNotCounted() {
        when(kpiService.increment(COMPANY_A, KpiMetric.LOW_STOCK_ALERTS, "msg-1")).thenReturn(Optional.empty());

        service.dispatch(COMPANY_A, lowStock, KpiMetric.LOW_STOCK_ALERTS, "msg-1");

        verify(messagingTemplate).convertAndSend("/topic/company/1/alerts", (Object) lowStock);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/company/1/kpi"), any(Object.class));
    }

    @Test
    void eventWithoutMetricDoesNotTouchKpi() {
        service.dispatch(COMPANY_A, lowStock, null, "msg-1");

        verify(messagingTemplate).convertAndSend("/topic/company/1/alerts", (Object) lowStock);
        verifyNoInteractions(kpiService);
    }

    @Test
    void brokerChannelErrorIsLoggedNotThrown() {
        doThrow(new MessageDeliveryException("kênh broker đầy"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> service.publish(COMPANY_A, lowStock)).doesNotThrowAnyException();
    }

    @Test
    void notificationOfCompanyANeverReachesTopicsOfCompanyB() {
        KpiSnapshot snapshot = new KpiSnapshot(LocalDate.parse("2026-11-05"), 0, 0, 0, 1, 0, NOW);
        when(kpiService.increment(COMPANY_A, KpiMetric.LOW_STOCK_ALERTS, "msg-1")).thenReturn(Optional.of(snapshot));

        service.dispatch(COMPANY_A, lowStock, KpiMetric.LOW_STOCK_ALERTS, "msg-1");

        verify(messagingTemplate, never()).convertAndSend(
                argThat((String destination) -> destination.startsWith("/topic/company/2/")), any(Object.class));
        verify(kpiService, never()).increment(eq(COMPANY_B), any(), any());
    }

    @Test
    void topicsAreMappedOneToOnePerCompany() {
        assertThat(RealtimeEventType.LOW_STOCK.topic(7L)).isEqualTo("/topic/company/7/alerts");
        assertThat(RealtimeEventType.ORDER_CREATED.topic(7L)).isEqualTo("/topic/company/7/orders");
        assertThat(RealtimeEventType.ORDER_DISPATCHED.topic(7L)).isEqualTo("/topic/company/7/orders");
        assertThat(RealtimeEventType.INVENTORY_UPDATED.topic(7L)).isEqualTo("/topic/company/7/inventory");
        assertThat(RealtimeEventType.KPI_UPDATED.topic(7L)).isEqualTo("/topic/company/7/kpi");
        assertThatThrownBy(() -> RealtimeEventType.LOW_STOCK.topic(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
