package vn.wms.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.notification.messaging.NotificationMessage;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tra topology RabbitMQ và bộ chuyển đổi JSON mà không cần broker. */
class RabbitMqConfigTest {

    private final RabbitMqConfig config = new RabbitMqConfig();
    private final NotificationProperties properties = new NotificationProperties(
            new NotificationProperties.Mail("no-reply@wms.local"),
            new NotificationProperties.Rabbitmq("wms.events", "notification.alerts", "alert.#"));

    @Test
    void bindingConnectsDurableQueueToTopicExchangeWithRoutingKey() {
        TopicExchange exchange = config.wmsEventsExchange(properties);
        Queue queue = config.notificationQueue(properties);

        Binding binding = config.notificationBinding(queue, exchange, properties);

        assertThat(queue.isDurable()).isTrue();
        assertThat(binding.getExchange()).isEqualTo("wms.events");
        assertThat(binding.getDestination()).isEqualTo("notification.alerts");
        assertThat(binding.getRoutingKey()).isEqualTo("alert.#");
    }

    @Test
    void declaresOrderAndInventoryQueuesLikeDefinitionsFile() {
        TopicExchange exchange = config.wmsEventsExchange(properties);
        Queue orderQueue = config.notificationOrderEventsQueue();
        Queue inventoryQueue = config.notificationInventoryEventsQueue();

        Binding orderBinding = config.notificationOrderEventsBinding(orderQueue, exchange);
        Binding inventoryBinding = config.notificationInventoryEventsBinding(inventoryQueue, exchange);

        // definitions.json: durable, không auto-delete, không arguments (dead-letter đi qua policy)
        for (Queue queue : new Queue[]{orderQueue, inventoryQueue}) {
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.isAutoDelete()).isFalse();
            assertThat(queue.isExclusive()).isFalse();
            assertThat(queue.getArguments()).isEmpty();
        }
        assertThat(orderBinding.getExchange()).isEqualTo(WmsEvents.EXCHANGE);
        assertThat(orderBinding.getDestination()).isEqualTo(WmsEvents.Queues.NOTIFICATION_ORDER_EVENTS);
        assertThat(orderBinding.getRoutingKey()).isEqualTo("order.#");
        assertThat(inventoryBinding.getExchange()).isEqualTo(WmsEvents.EXCHANGE);
        assertThat(inventoryBinding.getDestination()).isEqualTo(WmsEvents.Queues.NOTIFICATION_INVENTORY_EVENTS);
        assertThat(inventoryBinding.getRoutingKey()).isEqualTo("inventory.#");
    }

    @Test
    void converterUsesListenerParameterTypeAndIgnoresForeignTypeIdHeader() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MessageConverter converter = config.jsonMessageConverter(objectMapper);
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "eventType", "LOW_STOCK",
                "recipient", "quan.ly.kho@wms.local",
                "subject", "Cảnh báo tồn kho thấp",
                "content", "SKU-001 chỉ còn 3 cái"));
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        // Class của service gửi, notification-service không có class này trên classpath
        messageProperties.setHeader("__TypeId__", "vn.wms.inventory.event.LowStockEvent");
        messageProperties.setInferredArgumentType(NotificationMessage.class);

        Object converted = converter.fromMessage(new Message(body, messageProperties));

        assertThat(converted).isEqualTo(new NotificationMessage(
                "LOW_STOCK", "quan.ly.kho@wms.local", "Cảnh báo tồn kho thấp", "SKU-001 chỉ còn 3 cái"));
    }
}
