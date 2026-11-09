package vn.wms.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.tenant.TenantContext;
import vn.wms.common.tenant.TenantMessagePostProcessor;

import java.io.IOException;
import java.util.List;

/**
 * Consumer của queue {@code inventory.order-events} (nhận mọi routing key {@code order.#}).
 * Phân loại theo routing key rồi đọc JSON thành record sự kiện tương ứng và chuyển cho các {@link OrderEventHandler}.
 * Nhận message thô thay vì để converter tự đoán kiểu vì một queue chứa nhiều loại sự kiện.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectMapper objectMapper;
    private final List<OrderEventHandler> handlers;

    public OrderEventsListener(ObjectMapper objectMapper, List<OrderEventHandler> handlers) {
        this.objectMapper = objectMapper;
        this.handlers = List.copyOf(handlers);
    }

    @RabbitListener(queues = WmsEvents.Queues.INVENTORY_ORDER_EVENTS, id = "inventoryOrderEvents")
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String routingKey = properties.getReceivedRoutingKey() == null ? "" : properties.getReceivedRoutingKey();
        String messageId = properties.getMessageId();

        boolean contextSetHere = false;
        if (!TenantContext.isSet()) {
            Long companyId = TenantMessagePostProcessor.companyIdOf(message);
            if (companyId != null) {
                TenantContext.setCompanyId(companyId);
                contextSetHere = true;
            }
        }

        try {
            if (!TenantContext.isSet()) {
                // Không được bỏ qua (ack) vì message sẽ mất hẳn: từ chối không đẩy lại queue để broker chuyển sang
                // wms.dlx -> wms.dead-letter, người vận hành xem lại và phát lại khi đã bổ sung doanh nghiệp.
                log.warn("Từ chối sự kiện thiếu thông tin doanh nghiệp (x-wms-company-id), chuyển dead-letter: routingKey='{}' messageId={}",
                        routingKey, messageId);
                throw new AmqpRejectAndDontRequeueException(
                        "Sự kiện thiếu thông tin doanh nghiệp (header x-wms-company-id), routingKey='" + routingKey
                                + "', messageId=" + messageId + ": không xử lý được, chuyển dead-letter");
            }

            switch (routingKey) {
                case WmsEvents.RoutingKeys.ORDER_CREATED -> {
                    OrderCreatedEvent event = read(message, OrderCreatedEvent.class);
                    log.info("Nhận {} cho đơn {} (messageId={}, companyId={}), chuyển cho {} handler",
                            routingKey, event.orderNumber(), messageId, TenantContext.getCompanyId(), handlers.size());
                    handlers.forEach(handler -> handler.onOrderCreated(event, messageId));
                }
                case WmsEvents.RoutingKeys.ORDER_DISPATCHED -> {
                    OrderDispatchedEvent event = read(message, OrderDispatchedEvent.class);
                    log.info("Nhận {} cho đơn {} (messageId={}, companyId={}), chuyển cho {} handler",
                            routingKey, event.orderNumber(), messageId, TenantContext.getCompanyId(), handlers.size());
                    handlers.forEach(handler -> handler.onOrderDispatched(event, messageId));
                }
                default -> log.warn("Bỏ qua sự kiện chưa hỗ trợ: routingKey='{}' messageId={}", routingKey, messageId);
            }
        } finally {
            if (contextSetHere) {
                TenantContext.clear();
            }
        }
    }

    private <T> T read(Message message, Class<T> type) {
        try {
            return objectMapper.readValue(message.getBody(), type);
        } catch (IOException ex) {
            // Nội dung hỏng thì thử lại cũng vô ích: từ chối không đẩy lại queue để broker chuyển sang wms.dlx
            throw new AmqpRejectAndDontRequeueException(
                    "Nội dung sự kiện không đọc được thành " + type.getSimpleName() + ", chuyển dead-letter", ex);
        }
    }
}
