package vn.wms.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.notification.config.NotificationProperties;
import vn.wms.notification.dto.NotificationInfoResponse;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Nhận sự kiện RabbitMQ và gửi email cảnh báo")
public class NotificationController {

    private final NotificationProperties properties;
    private final String applicationName;
    private final String mailHost;

    public NotificationController(NotificationProperties properties,
                                  @Value("${spring.application.name}") String applicationName,
                                  @Value("${spring.mail.host:}") String mailHost) {
        this.properties = properties;
        this.applicationName = applicationName;
        this.mailHost = mailHost;
    }

    @GetMapping("/health-info")
    @Operation(summary = "Thông tin cấu hình RabbitMQ và email đang chạy (kiểm tra nhanh khi tích hợp)")
    public ApiResponse<NotificationInfoResponse> healthInfo() {
        NotificationProperties.Rabbitmq rabbit = properties.rabbitmq();
        return ApiResponse.ok(new NotificationInfoResponse(applicationName, rabbit.exchange(), rabbit.queue(),
                rabbit.routingKey(), mailHost, properties.mail().from()));
    }
}
