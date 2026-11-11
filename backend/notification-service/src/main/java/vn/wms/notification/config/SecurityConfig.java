package vn.wms.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Bảo mật của notification-service (WMS-20.3): chuỗi bộ lọc dùng chung của module common
 * ({@code WmsSecurityAutoConfiguration}) đọc danh tính từ header của api-gateway và trả 401/403 theo khung
 * ApiResponse. {@code @EnableMethodSecurity} bật {@code @PreAuthorize} trên controller (ma trận ở docs/SECURITY.md).
 * Hub WebSocket {@code /ws-wms/**} nằm trong {@code app.security.public-paths}: trình duyệt không gửi được header
 * Bearer khi bắt tay, JWT được kiểm tra ở frame STOMP CONNECT ({@code StompAuthChannelInterceptor}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
}
