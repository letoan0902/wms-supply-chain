package vn.wms.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Kiểm tra listener phân loại sự kiện theo routing key và xử lý message hỏng, không cần broker. */
class OrderEventsListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final OrderEventHandler handler = mock(OrderEventHandler.class);
    private final OrderEventsListener listener = new OrderEventsListener(objectMapper, List.of(handler));

    private final List<OrderLine> items = List.of(
            new OrderLine(100L, "SKU-001", "Thùng carton 60x40", new BigDecimal("50"), "cái", new BigDecimal("24000.00")));

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void dispatchesOrderCreatedToHandlers() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(1L, "PO-20261104-0001", OrderType.PURCHASE, "APPROVED", 10L,
                null, 5L, "Kho Hà Nội", new BigDecimal("1200000.00"), "purchasing",
                Instant.parse("2026-11-04T08:00:00Z"), items);

        listener.onMessage(message(objectMapper.writeValueAsBytes(event), WmsEvents.RoutingKeys.ORDER_CREATED, "msg-1"));

        verify(handler).onOrderCreated(event, "msg-1");
        verify(handler, never()).onOrderDispatched(any(), any());
    }

    @Test
    void dispatchesOrderDispatchedToHandlers() throws Exception {
        OrderDispatchedEvent event = new OrderDispatchedEvent(2L, "SO-20261104-0007", OrderType.SALES, 5L, "staff",
                Instant.parse("2026-11-04T09:30:00Z"), items);

        listener.onMessage(message(objectMapper.writeValueAsBytes(event), WmsEvents.RoutingKeys.ORDER_DISPATCHED, "msg-2"));

        verify(handler).onOrderDispatched(event, "msg-2");
        verify(handler, never()).onOrderCreated(any(), any());
    }

    @Test
    void ignoresUnknownRoutingKeyWithoutFailing() {
        assertThatCode(() -> listener.onMessage(message("{}".getBytes(StandardCharsets.UTF_8), "order.cancelled", "msg-3")))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onMessage(message("{}".getBytes(StandardCharsets.UTF_8), null, "msg-4")))
                .doesNotThrowAnyException();
        verifyNoInteractions(handler);
    }

    @Test
    void rejectsMalformedPayloadWithoutRequeue() {
        Message broken = message("{khong phai json".getBytes(StandardCharsets.UTF_8), WmsEvents.RoutingKeys.ORDER_CREATED, "msg-5");

        assertThatThrownBy(() -> listener.onMessage(broken)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(handler);
    }

    @Test
    void worksWhenNoHandlerIsRegisteredYet() throws Exception {
        OrderEventsListener withoutHandlers = new OrderEventsListener(objectMapper, List.of());
        OrderCreatedEvent event = new OrderCreatedEvent(1L, "PO-20261104-0001", OrderType.PURCHASE, "APPROVED", 10L,
                null, 5L, "Kho Hà Nội", new BigDecimal("1200000.00"), "purchasing", Instant.now(), items);

        assertThatCode(() -> withoutHandlers.onMessage(
                message(objectMapper.writeValueAsBytes(event), WmsEvents.RoutingKeys.ORDER_CREATED, "msg-6")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMessageWithoutCompanyIdToDeadLetterInsteadOfDroppingIt() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(1L, "PO-20261104-0001", OrderType.PURCHASE, "APPROVED", 10L,
                null, 5L, "Kho Hà Nội", new BigDecimal("1200000.00"), "purchasing", Instant.now(), items);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setReceivedRoutingKey(WmsEvents.RoutingKeys.ORDER_CREATED);
        properties.setMessageId("msg-no-tenant");
        Message msg = new Message(objectMapper.writeValueAsBytes(event), properties);

        TenantContext.clear();

        // Ném AmqpRejectAndDontRequeueException để broker chuyển message sang wms.dlx thay vì ack rồi bỏ mất
        assertThatThrownBy(() -> listener.onMessage(msg))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("thiếu thông tin doanh nghiệp")
                .hasMessageContaining("msg-no-tenant");
        verifyNoInteractions(handler);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void usesCompanyIdFromHeaderAndClearsContextAfterwards() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(1L, "PO-20261104-0001", OrderType.PURCHASE, "APPROVED", 10L,
                null, 5L, "Kho Hà Nội", new BigDecimal("1200000.00"), "purchasing", Instant.now(), items);
        Long[] companySeenByHandler = new Long[1];
        OrderEventHandler capturing = new OrderEventHandler() {
            @Override
            public void onOrderCreated(OrderCreatedEvent e, String messageId) {
                companySeenByHandler[0] = TenantContext.getCompanyId();
            }

            @Override
            public void onOrderDispatched(OrderDispatchedEvent e, String messageId) {
            }
        };
        OrderEventsListener withCapturing = new OrderEventsListener(objectMapper, List.of(capturing));

        TenantContext.clear();
        withCapturing.onMessage(message(objectMapper.writeValueAsBytes(event), WmsEvents.RoutingKeys.ORDER_CREATED, "msg-7"));

        assertThat(companySeenByHandler[0]).isEqualTo(1L);
        assertThat(TenantContext.isSet()).isFalse();
    }

    private static Message message(byte[] body, String routingKey, String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setReceivedRoutingKey(routingKey);
        properties.setMessageId(messageId);
        properties.setHeader(WmsEventPublisher.HEADER_COMPANY_ID, 1L);
        return new Message(body, properties);
    }
}
