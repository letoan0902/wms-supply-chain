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

    /**
     * Người gửi email.
     *
     * @param from     địa chỉ (biến MAIL_FROM); gửi qua Brevo thì phải là sender đã xác minh
     * @param fromName tên hiển thị (biến MAIL_FROM_NAME), dùng khi gửi qua Brevo
     */
    public record Mail(@DefaultValue("no-reply@wms.local") String from, @DefaultValue("WMS Supply Chain") String fromName) {
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
