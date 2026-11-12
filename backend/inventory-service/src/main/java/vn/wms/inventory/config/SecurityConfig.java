package vn.wms.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Bảo mật của inventory-service (WMS-20.3): chuỗi bộ lọc dùng chung của module common
 * ({@code WmsSecurityAutoConfiguration}) đọc danh tính từ header của api-gateway và trả 401/403 theo khung
 * ApiResponse. {@code @EnableMethodSecurity} bật {@code @PreAuthorize} trên controller (vai trò được gọi từng API,
 * ma trận ở docs/SECURITY.md); phạm vi kho vẫn kiểm tra trong service qua {@code UserContext}
 * ({@code resolveAllowedWarehouses}, {@code requireWarehouseWriteAccess}, {@code StocktakePermissions},
 * {@code ReportAccess}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
}
