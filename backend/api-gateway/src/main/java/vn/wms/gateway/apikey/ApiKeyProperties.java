package vn.wms.gateway.apikey;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Cấu hình xác thực bằng API key tại gateway (tiền tố gateway.api-key), xem {@link ApiKeyAuthenticationGlobalFilter}.
 *
 * @param enabled            bật/tắt xác thực bằng API key (GATEWAY_API_KEY_ENABLED). Tắt thì header X-Api-Key bị bỏ
 *                           qua và request đi tiếp như không có key (bộ lọc JWT quyết định)
 * @param rateLimitPerSecond số request tối đa mỗi giây của một key (API_KEY_RATE_LIMIT_PER_SECOND, mặc định 20);
 *                           vượt thì trả 429. Giá trị nhỏ hơn 1 tắt giới hạn
 * @param allowedPaths       đường dẫn (kiểu Ant) API key được gọi bằng GET; mọi đường dẫn hoặc phương thức khác
 *                           trả 403
 */
@ConfigurationProperties(prefix = "gateway.api-key")
public record ApiKeyProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int rateLimitPerSecond,
        @DefaultValue({"/api/products/**", "/api/inventory/**", "/api/orders/**"}) List<String> allowedPaths) {
}
