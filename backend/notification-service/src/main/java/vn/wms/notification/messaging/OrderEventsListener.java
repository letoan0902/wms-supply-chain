package vn.wms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.RealtimeNotificationFactory;
import vn.wms.notification.realtime.RealtimeNotificationService;

/**
 * Consumer của queue {@code notification.order-events} (nhận mọi routing key {@code order.#}).
 * Phân loại theo routing key, đọc JSON thành record sự kiện rồi đẩy phong bì lên {@code /topic/orders} và tăng KPI.
 * Routing key chưa hỗ trợ thì ghi log và bỏ qua (ack); JSON hỏng thì chuyển dead-letter.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectReader reader;
    private final RealtimeNotificationFactory factory;
    private final RealtimeNotificationService realtimeService;

    public OrderEventsListener(ObjectMapper objectMapper, RealtimeNotificationFactory factory,
                               RealtimeNotificationService realtimeService) {
        this.reader = objectMapper.reader();
        this.factory = factory;
        this.realtimeService = realtimeService;
    }

    @RabbitListener(queues = WmsEvents.Queues.NOTIFICATION_ORDER_EVENTS, id = "notificationOrderEvents")
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String eventType = EventMessages.eventType(properties);
        String messageId = properties.getMessageId();
        switch (eventType) {
            case WmsEvents.RoutingKeys.ORDER_CREATED -> {
                OrderCreatedEvent event = EventMessages.read(reader, message, OrderCreatedEvent.class);
                log.info("Nhận {} cho đơn {} (messageId={}), đẩy lên WebSocket", eventType, event.orderNumber(), messageId);
                realtimeService.dispatch(factory.orderCreated(event, messageId, EventMessages.timestamp(properties)),
                        KpiMetric.forNewOrder(event.orderType()), messageId);
            }
            case WmsEvents.RoutingKeys.ORDER_DISPATCHED -> {
                OrderDispatchedEvent event = EventMessages.read(reader, message, OrderDispatchedEvent.class);
                log.info("Nhận {} cho đơn {} (messageId={}), đẩy lên WebSocket", eventType, event.orderNumber(), messageId);
                realtimeService.dispatch(factory.orderDispatched(event, messageId, EventMessages.timestamp(properties)),
                        KpiMetric.DISPATCHED_ORDERS, messageId);
            }
            default -> log.warn("Bỏ qua sự kiện đơn hàng chưa hỗ trợ: routingKey='{}' messageId={}", eventType, messageId);
        }
    }
}
