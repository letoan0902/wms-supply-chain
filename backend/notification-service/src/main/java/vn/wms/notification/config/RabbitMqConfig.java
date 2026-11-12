package vn.wms.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.wms.common.messaging.WmsEvents;

/**
 * Khai báo exchange, queue, binding và bộ chuyển đổi JSON cho RabbitMQ. Tham số khớp docker/rabbitmq/definitions.json
 * (durable, không arguments) để chạy service rời với broker trống vẫn được mà không lệch tham số.
 */
@Configuration
public class RabbitMqConfig {

    /** Exchange kiểu topic dùng chung cho các sự kiện của hệ thống WMS. */
    @Bean
    TopicExchange wmsEventsExchange(NotificationProperties properties) {
        return new TopicExchange(properties.rabbitmq().exchange(), true, false);
    }

    @Bean
    Queue notificationQueue(NotificationProperties properties) {
        return QueueBuilder.durable(properties.rabbitmq().queue()).build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange wmsEventsExchange,
                                NotificationProperties properties) {
        return BindingBuilder.bind(notificationQueue).to(wmsEventsExchange).with(properties.rabbitmq().routingKey());
    }

    /** Queue nhận mọi sự kiện đơn hàng (order.#) để đẩy WebSocket, xem OrderEventsListener. */
    @Bean
    Queue notificationOrderEventsQueue() {
        return QueueBuilder.durable(WmsEvents.Queues.NOTIFICATION_ORDER_EVENTS).build();
    }

    @Bean
    Binding notificationOrderEventsBinding(Queue notificationOrderEventsQueue, TopicExchange wmsEventsExchange) {
        return BindingBuilder.bind(notificationOrderEventsQueue).to(wmsEventsExchange)
                .with(WmsEvents.RoutingKeys.ORDER_ALL);
    }

    /** Queue nhận mọi sự kiện tồn kho (inventory.#) để đẩy WebSocket, xem InventoryEventsListener. */
    @Bean
    Queue notificationInventoryEventsQueue() {
        return QueueBuilder.durable(WmsEvents.Queues.NOTIFICATION_INVENTORY_EVENTS).build();
    }

    @Bean
    Binding notificationInventoryEventsBinding(Queue notificationInventoryEventsQueue, TopicExchange wmsEventsExchange) {
        return BindingBuilder.bind(notificationInventoryEventsQueue).to(wmsEventsExchange)
                .with(WmsEvents.RoutingKeys.INVENTORY_ALL);
    }

    /** Queue nhận yêu cầu gửi email tài khoản (auth.#) của auth-service, xem AccountEmailListener (WMS-23.6). */
    @Bean
    Queue notificationAuthEmailsQueue() {
        return QueueBuilder.durable(WmsEvents.Queues.NOTIFICATION_AUTH_EMAILS).build();
    }

    @Bean
    Binding notificationAuthEmailsBinding(Queue notificationAuthEmailsQueue, TopicExchange wmsEventsExchange) {
        return BindingBuilder.bind(notificationAuthEmailsQueue).to(wmsEventsExchange)
                .with(WmsEvents.RoutingKeys.AUTH_ALL);
    }

    /**
     * Chuyển đổi JSON hai chiều. Dùng INFERRED để kiểu đích lấy từ tham số của phương thức @RabbitListener,
     * nhờ đó không phụ thuộc header __TypeId__ (tên class Java của service gửi, service nhận không có).
     */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
