package vn.wms.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Bảo mật của order-service (WMS-20.3): chuỗi bộ lọc dùng chung của module common
 * ({@code WmsSecurityAutoConfiguration}) đọc danh tính từ header của api-gateway và trả 401/403 theo khung
 * ApiResponse. {@code @EnableMethodSecurity} bật {@code @PreAuthorize} trên controller: mỗi API khai báo vai trò được
 * gọi theo ma trận ở docs/SECURITY.md.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
}
