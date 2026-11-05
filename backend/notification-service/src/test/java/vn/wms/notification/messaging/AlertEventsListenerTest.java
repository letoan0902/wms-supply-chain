package vn.wms.notification.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.LowStockAlertEvent;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.NotificationSeverity;
import vn.wms.notification.realtime.RealtimeEventType;
import vn.wms.notification.realtime.RealtimeNotification;
import vn.wms.notification.realtime.RealtimeNotificationService;
import vn.wms.notification.service.NotificationService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.wms.notification.messaging.ListenerTestSupport.json;
import static vn.wms.notification.messaging.ListenerTestSupport.message;

/** Listener duy nhất của notification.alerts: cảnh báo tồn thấp lên WebSocket, định dạng email cũ vẫn gửi email. */
class AlertEventsListenerTest {

    private final RealtimeNotificationService realtimeService = mock(RealtimeNotificationService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AlertEventsListener listener = new AlertEventsListener(ListenerTestSupport.OBJECT_MAPPER,
            ListenerTestSupport.factory(), realtimeService, notificationService);

    private final LowStockAlertEvent lowStock = new LowStockAlertEvent(5L, "Kho Hà Nội", 100L, "SKU-001",
            "Thùng carton", new BigDecimal("5"), new BigDecimal("20"), Instant.parse("2026-11-05T07:59:00Z"));

    @Test
    void lowStockEventIsPushedToAlertsTopicAndNotEmailed() throws Exception {
        listener.onMessage(json(lowStock, WmsEvents.RoutingKeys.ALERT_LOW_STOCK, "msg-1"));

        ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
        verify(realtimeService).dispatch(captor.capture(), eq(KpiMetric.LOW_STOCK_ALERTS), eq("msg-1"));
        RealtimeNotification notification = captor.getValue();
        assertThat(notification.type()).isEqualTo(RealtimeEventType.LOW_STOCK);
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(notification.message()).isEqualTo("SKU-001 Thùng carton tại Kho Hà Nội còn 5, dưới ngưỡng 20");
        assertThat(notification.payload()).isEqualTo(lowStock);
        verifyNoInteractions(notificationService);
    }

    @Test
    void eventTypeHeaderIsUsedWhenRoutingKeyIsMissing() throws Exception {
        Message message = json(lowStock, null, "msg-2");
        message.getMessageProperties().setHeader(WmsEventPublisher.HEADER_EVENT_TYPE, WmsEvents.RoutingKeys.ALERT_LOW_STOCK);

        listener.onMessage(message);

        verify(realtimeService).dispatch(any(RealtimeNotification.class), eq(KpiMetric.LOW_STOCK_ALERTS), eq("msg-2"));
    }

    @Test
    void legacyEmailMessageOnOtherAlertKeyStillSendsEmail() throws Exception {
        NotificationMessage email = new NotificationMessage(
                "LOW_STOCK", "quan.ly.kho@wms.local", "Cảnh báo tồn kho thấp", "SKU-001 chỉ còn 3 cái");

        listener.onMessage(json(email, "alert.email", "msg-3"));

        verify(notificationService).process(email);
        verifyNoInteractions(realtimeService);
    }

    @Test
    void unknownAlertWithoutRecipientIsIgnored() throws Exception {
        assertThatCode(() -> listener.onMessage(json(Map.of("skuCode", "SKU-001", "level", "HIGH"),
                "alert.over-stock", "msg-4")))
                .doesNotThrowAnyException();
        verify(notificationService, never()).process(any());
        verifyNoInteractions(realtimeService);
    }

    @Test
    void rejectsMalformedPayloadWithoutRequeueOnBothBranches() {
        byte[] broken = "{khong phai json".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> listener.onMessage(message(broken, WmsEvents.RoutingKeys.ALERT_LOW_STOCK, "msg-5")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(message(broken, "alert.email", "msg-6")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(realtimeService, notificationService);
    }
}
