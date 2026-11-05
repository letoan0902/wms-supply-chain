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
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.notification.realtime.KpiMetric;
import vn.wms.notification.realtime.RealtimeNotificationFactory;
import vn.wms.notification.realtime.RealtimeNotificationService;

/**
 * Consumer của queue {@code notification.inventory-events} (nhận mọi routing key {@code inventory.#}).
 * Hiện xử lý {@code inventory.updated}: đẩy phong bì lên {@code /topic/inventory} và tăng KPI biến động tồn kho.
 * Routing key chưa hỗ trợ thì ghi log và bỏ qua (ack); JSON hỏng thì chuyển dead-letter.
 */
@Component
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final ObjectReader reader;
    private final RealtimeNotificationFactory factory;
    private final RealtimeNotificationService realtimeService;

    public InventoryEventsListener(ObjectMapper objectMapper, RealtimeNotificationFactory factory,
                                   RealtimeNotificationService realtimeService) {
        this.reader = objectMapper.reader();
        this.factory = factory;
        this.realtimeService = realtimeService;
    }

    @RabbitListener(queues = WmsEvents.Queues.NOTIFICATION_INVENTORY_EVENTS, id = "notificationInventoryEvents")
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String eventType = EventMessages.eventType(properties);
        String messageId = properties.getMessageId();
        if (WmsEvents.RoutingKeys.INVENTORY_UPDATED.equals(eventType)) {
            InventoryUpdatedEvent event = EventMessages.read(reader, message, InventoryUpdatedEvent.class);
            log.info("Nhận {} cho {} tại kho {} (messageId={}), đẩy lên WebSocket",
                    eventType, event.skuCode(), event.warehouseId(), messageId);
            realtimeService.dispatch(factory.inventoryUpdated(event, messageId, EventMessages.timestamp(properties)),
                    KpiMetric.INVENTORY_MOVEMENTS, messageId);
            return;
        }
        log.warn("Bỏ qua sự kiện tồn kho chưa hỗ trợ: routingKey='{}' messageId={}", eventType, messageId);
    }
}
