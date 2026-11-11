package vn.wms.gateway.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Cấu hình kiểm tra thuê bao tại gateway (tiền tố gateway.subscription), xem {@link SubscriptionGlobalFilter}.
 *
 * @param enabled           bật/tắt bộ lọc (biến môi trường GATEWAY_SUBSCRIPTION_ENABLED). Tắt thì gateway không gắn
 *                          X-Plan và không chặn request ghi khi thuê bao hết hạn
 * @param writeAllowedPaths các đường dẫn (kiểu Ant) vẫn được gọi bằng POST, PUT, PATCH, DELETE khi thuê bao đã hết hạn:
 *                          đăng nhập, làm mới token, đăng xuất, đổi mật khẩu, API thuê bao và thanh toán
 *                          (docs/SAAS_SPEC.md, mục 2.6), và thu hồi API key ({@code DELETE /api/auth/api-keys/{id}})
 */
@ConfigurationProperties(prefix = "gateway.subscription")
public record SubscriptionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue({
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/api/users/change-password",
                "/api/auth/users/change-password",
                "/api/auth/subscription/**",
                "/api/auth/payments/**",
                "/api/auth/api-keys/*"}) List<String> writeAllowedPaths) {
}
