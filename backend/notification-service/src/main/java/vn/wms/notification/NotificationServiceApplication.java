package vn.wms.notification;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Notification Service (cổng 8085): lắng nghe sự kiện từ RabbitMQ (đơn hàng, tồn kho, cảnh báo tồn kho thấp),
 * gửi email cảnh báo và đẩy thông báo thời gian thực qua hub WebSocket STOMP /ws-wms (docs/MESSAGING.md mục 8).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(info = @Info(title = "WMS Notification Service API", version = "v1",
        description = "Nhận sự kiện từ RabbitMQ, gửi email cảnh báo và đẩy thông báo WebSocket /ws-wms"))
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
