package vn.wms.notification;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Notification Service (cổng 8085): lắng nghe sự kiện từ RabbitMQ (ví dụ cảnh báo tồn kho thấp)
 * và gửi email cảnh báo cho người nhận.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(info = @Info(title = "WMS Notification Service API", version = "v1",
        description = "Nhận sự kiện từ RabbitMQ và gửi email cảnh báo"))
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
