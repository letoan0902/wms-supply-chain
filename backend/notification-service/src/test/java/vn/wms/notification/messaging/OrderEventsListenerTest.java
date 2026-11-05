package vn.wms.notification.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.NotificationSeverity;
import vn.wms.notification.realtime.RealtimeEventType;
import vn.wms.notification.realtime.RealtimeNotification;
import vn.wms.notification.realtime.RealtimeNotificationService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.wms.notification.messaging.ListenerTestSupport.json;
import static vn.wms.notification.messaging.ListenerTestSupport.message;

/** Listener của notification.order-events: phân loại theo routing key, JSON hỏng và routing key lạ; không cần broker. */
class OrderEventsListenerTest {

    private final RealtimeNotificationService realtimeService = mock(RealtimeNotificationService.class);
    private final OrderEventsListener listener = new OrderEventsListener(
            ListenerTestSupport.OBJECT_MAPPER, ListenerTestSupport.factory(), realtimeService);

    private final List<OrderLine> items = List.of(
            new OrderLine(100L, "SKU-001", "Thùng carton", new BigDecimal("50"), "cái", new BigDecimal("24000.00")));

    @Test
    void salesOrderCreatedIsPushedAndCountedAsSalesOrder() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(2L, "SO-20261105-0002", OrderType.SALES, "CONFIRMED", null,
                20L, 5L, "Kho Hà Nội", new BigDecimal("500000"), "sales", Instant.parse("2026-11-05T07:00:00Z"), items);

        listener.onMessage(json(event, WmsEvents.RoutingKeys.ORDER_CREATED, "msg-1"));

        ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
        verify(realtimeService).dispatch(captor.capture(), eq(KpiMetric.SALES_ORDERS), eq("msg-1"));
        RealtimeNotification notification = captor.getValue();
        assertThat(notification.id()).isEqualTo("msg-1");
        assertThat(notification.type()).isEqualTo(RealtimeEventType.ORDER_CREATED);
        assertThat(notification.title()).isEqualTo("Đơn bán hàng mới");
        assertThat(notification.payload()).isEqualTo(event);
    }

    @Test
    void purchaseOrderCreatedIsCountedAsPurchaseOrder() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(1L, "PO-20261105-0001", OrderType.PURCHASE, "APPROVED", 10L,
                null, 5L, "Kho Hà Nội", null, "purchasing", null, items);

        listener.onMessage(json(event, WmsEvents.RoutingKeys.ORDER_CREATED, "msg-2"));

        verify(realtimeService).dispatch(argThat(
                notification -> "Đơn mua hàng mới".equals(notification.title())), eq(KpiMetric.PURCHASE_ORDERS), eq("msg-2"));
    }

    @Test
    void orderDispatchedIsPushedAsSuccess() throws Exception {
        OrderDispatchedEvent event = new OrderDispatchedEvent(2L, "SO-20261105-0002", OrderType.SALES, 5L, "staff",
                Instant.parse("2026-11-05T09:30:00Z"), items);

        listener.onMessage(json(event, WmsEvents.RoutingKeys.ORDER_DISPATCHED, "msg-3"));

        ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
        verify(realtimeService).dispatch(captor.capture(), eq(KpiMetric.DISPATCHED_ORDERS), eq("msg-3"));
        assertThat(captor.getValue().type()).isEqualTo(RealtimeEventType.ORDER_DISPATCHED);
        assertThat(captor.getValue().severity()).isEqualTo(NotificationSeverity.SUCCESS);
    }

    @Test
    void ignoresUnknownRoutingKeyWithoutFailing() {
        assertThatCode(() -> listener.onMessage(message("{}".getBytes(StandardCharsets.UTF_8), "order.cancelled", "msg-4")))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onMessage(message("{}".getBytes(StandardCharsets.UTF_8), null, "msg-5")))
                .doesNotThrowAnyException();
        verifyNoInteractions(realtimeService);
    }

    @Test
    void rejectsMalformedPayloadWithoutRequeue() {
        assertThatThrownBy(() -> listener.onMessage(message("{khong phai json".getBytes(StandardCharsets.UTF_8),
                WmsEvents.RoutingKeys.ORDER_CREATED, "msg-6")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(message("null".getBytes(StandardCharsets.UTF_8),
                WmsEvents.RoutingKeys.ORDER_DISPATCHED, "msg-7")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(realtimeService);
    }
}
