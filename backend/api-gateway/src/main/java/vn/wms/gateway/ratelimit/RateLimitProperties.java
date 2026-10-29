package vn.wms.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình giới hạn tốc độ gọi API tại gateway (tiền tố gateway.rate-limit).
 *
 * <p>Ngưỡng (số request mỗi giây và mức bùng nổ) đặt trong application.yml ở phần
 * spring.cloud.gateway.default-filters (RequestRateLimiter dùng thuật toán token bucket trên Redis).
 *
 * @param enabled bật/tắt giới hạn tốc độ. Khi tắt, KeyResolver trả về rỗng nên RequestRateLimiter bỏ qua
 *                (deny-empty-key=false) và gateway không gọi Redis.
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled) {
}
