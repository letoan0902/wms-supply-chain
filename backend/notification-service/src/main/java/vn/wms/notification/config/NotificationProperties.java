package vn.wms.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình riêng của notification-service (tiền tố app).
 *
 * @param mail     cấu hình email gửi đi
 * @param rabbitmq cấu hình exchange/queue nhận sự kiện
 */
@ConfigurationProperties(prefix = "app")
public record NotificationProperties(@DefaultValue Mail mail, @DefaultValue Rabbitmq rabbitmq) {

    /** Địa chỉ người gửi (biến MAIL_FROM). */
    public record Mail(@DefaultValue("no-reply@wms.local") String from) {
    }

    /**
     * Topology RabbitMQ. Các service khác (ví dụ inventory-service) publish vào exchange với routing key
     * bắt đầu bằng "alert." thì queue của service này sẽ nhận được.
     */
    public record Rabbitmq(
            @DefaultValue("wms.events") String exchange,
            @DefaultValue("notification.alerts") String queue,
            @DefaultValue("alert.#") String routingKey) {
    }
}
