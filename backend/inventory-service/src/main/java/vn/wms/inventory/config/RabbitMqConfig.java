package vn.wms.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.wms.common.messaging.WmsEvents;

/**
 * Khai báo exchange, queue và binding mà inventory-service dùng, trùng từng tham số với
 * docker/rabbitmq/definitions.json (queue durable, không arguments; dead-letter đặt bằng policy phía broker;
 * binding theo từng routing key cụ thể) nên khai báo lại là idempotent. Nhờ vậy chạy service rời trong IDE
 * với broker trống vẫn không bị lỗi queue chưa tồn tại. Converter JSON và {@code WmsEventPublisher} do module
 * common tự cấu hình.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {

    @Bean
    TopicExchange wmsEventsExchange() {
        return new TopicExchange(WmsEvents.EXCHANGE, true, false);
    }

    @Bean
    Queue inventoryOrderEventsQueue() {
        return QueueBuilder.durable(WmsEvents.Queues.INVENTORY_ORDER_EVENTS).build();
    }

    @Bean
    Binding inventoryOrderCreatedBinding(Queue inventoryOrderEventsQueue, TopicExchange wmsEventsExchange) {
        return BindingBuilder.bind(inventoryOrderEventsQueue).to(wmsEventsExchange)
                .with(WmsEvents.RoutingKeys.ORDER_CREATED);
    }

    @Bean
    Binding inventoryOrderDispatchedBinding(Queue inventoryOrderEventsQueue, TopicExchange wmsEventsExchange) {
        return BindingBuilder.bind(inventoryOrderEventsQueue).to(wmsEventsExchange)
                .with(WmsEvents.RoutingKeys.ORDER_DISPATCHED);
    }

    @Bean
    Binding inventoryOrderReceivedBinding(Queue inventoryOrderEventsQueue, TopicExchange wmsEventsExchange) {
        return BindingBuilder.bind(inventoryOrderEventsQueue).to(wmsEventsExchange)
                .with(WmsEvents.RoutingKeys.ORDER_RECEIVED);
    }
}
