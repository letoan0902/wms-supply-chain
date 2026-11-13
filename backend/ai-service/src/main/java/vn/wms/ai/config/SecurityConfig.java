package vn.wms.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Bảo mật của ai-service, giống các service nghiệp vụ khác: api-gateway kiểm tra JWT và chuyển tiếp danh tính qua
 * header (X-User-Name, X-User-Roles, X-Company-Id). Chuỗi bộ lọc dùng chung của module common
 * ({@code WmsSecurityAutoConfiguration}, WMS-20.3) chặn request thiếu danh tính bằng 401 khi
 * {@code app.security.allow-anonymous-without-gateway=false} (máy chủ). Vai trò theo loại dữ liệu kiểm tra trong
 * service (không dùng {@code @PreAuthorize}); việc bắt buộc có doanh nghiệp do {@code TenantFilter} đảm nhận.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
}
