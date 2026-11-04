package vn.wms.order.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.wms.common.messaging.WmsEvents;

/**
 * order-service chỉ phát sự kiện (order.created, order.dispatched) nên chỉ cần khai báo exchange.
 * Topology đầy đủ (queue, binding, dead-letter) được nạp từ docker/rabbitmq/definitions.json; khai báo ở đây
 * là idempotent, giúp chạy service rời (IDE) với broker trống không bị lỗi exchange chưa tồn tại.
 * Converter JSON và {@code WmsEventPublisher} do module common tự cấu hình.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {

    @Bean
    TopicExchange wmsEventsExchange() {
        return new TopicExchange(WmsEvents.EXCHANGE, true, false);
    }
}
