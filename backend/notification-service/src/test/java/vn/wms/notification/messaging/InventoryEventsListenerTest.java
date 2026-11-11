package vn.wms.notification.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.RealtimeEventType;
import vn.wms.notification.realtime.RealtimeNotification;
import vn.wms.notification.realtime.RealtimeNotificationService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_A;
import static vn.wms.notification.messaging.ListenerTestSupport.COMPANY_B;
import static vn.wms.notification.messaging.ListenerTestSupport.json;
import static vn.wms.notification.messaging.ListenerTestSupport.message;
import static vn.wms.notification.messaging.ListenerTestSupport.ofCompany;
import static vn.wms.notification.messaging.ListenerTestSupport.withoutCompany;

/** Listener của notification.inventory-events; không cần broker. */
class InventoryEventsListenerTest {

    private final RealtimeNotificationService realtimeService = mock(RealtimeNotificationService.class);
    private final InventoryEventsListener listener = new InventoryEventsListener(
            ListenerTestSupport.OBJECT_MAPPER, ListenerTestSupport.factory(), realtimeService);

    @Test
    void inventoryUpdatedIsPushedAndCountedAsMovement() throws Exception {
        InventoryUpdatedEvent event = new InventoryUpdatedEvent(5L, 100L, "SKU-001", new BigDecimal("50"),
                new BigDecimal("45"), "GOODS_ISSUE", "SO-20261105-0002", Instant.parse("2026-11-05T09:31:00Z"));

        listener.onMessage(json(event, WmsEvents.RoutingKeys.INVENTORY_UPDATED, "msg-1"));

        ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
        verify(realtimeService).dispatch(eq(COMPANY_A), captor.capture(), eq(KpiMetric.INVENTORY_MOVEMENTS), eq("msg-1"));
        assertThat(captor.getValue().type()).isEqualTo(RealtimeEventType.INVENTORY_UPDATED);
        assertThat(captor.getValue().payload()).isEqualTo(event);
        assertThat(captor.getValue().occurredAt()).isEqualTo(Instant.parse("2026-11-05T09:31:00Z"));
    }

    @Test
    void ignoresUnknownRoutingKeyWithoutFailing() {
        assertThatCode(() -> listener.onMessage(message("{}".getBytes(StandardCharsets.UTF_8),
                "inventory.reserved", "msg-2")))
                .doesNotThrowAnyException();
        verifyNoInteractions(realtimeService);
    }

    @Test
    void rejectsMalformedPayloadWithoutRequeue() {
        assertThatThrownBy(() -> listener.onMessage(message("[1,2".getBytes(StandardCharsets.UTF_8),
                WmsEvents.RoutingKeys.INVENTORY_UPDATED, "msg-3")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(realtimeService);
    }

    @Test
    void eventWithoutCompanyIsSkippedWithoutExceptionOrRetry() throws Exception {
        InventoryUpdatedEvent event = new InventoryUpdatedEvent(5L, 100L, "SKU-001", new BigDecimal("50"),
                new BigDecimal("45"), "GOODS_ISSUE", null, null);

        assertThatCode(() -> listener.onMessage(withoutCompany(json(event, WmsEvents.RoutingKeys.INVENTORY_UPDATED,
                "msg-4")))).doesNotThrowAnyException();
        verifyNoInteractions(realtimeService);
    }

    @Test
    void inventoryEventOfCompanyBIsDispatchedForCompanyB() throws Exception {
        InventoryUpdatedEvent event = new InventoryUpdatedEvent(6L, 100L, "SKU-001", new BigDecimal("10"),
                new BigDecimal("12"), "GOODS_RECEIPT", null, null);

        listener.onMessage(ofCompany(json(event, WmsEvents.RoutingKeys.INVENTORY_UPDATED, "msg-5"), COMPANY_B));

        verify(realtimeService).dispatch(eq(COMPANY_B), org.mockito.ArgumentMatchers.any(RealtimeNotification.class),
                eq(KpiMetric.INVENTORY_MOVEMENTS), eq("msg-5"));
    }
}
