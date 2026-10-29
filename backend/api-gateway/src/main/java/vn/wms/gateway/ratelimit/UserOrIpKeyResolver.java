package vn.wms.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Xác định "ai" đang gọi để giới hạn tốc độ theo từng người dùng.
 *
 * <ul>
 *   <li>Đã đăng nhập (JwtAuthenticationGlobalFilter đã gắn header X-User-Name): khóa là user:&lt;tên đăng nhập&gt;.</li>
 *   <li>Chưa đăng nhập hoặc gateway đang tắt kiểm tra JWT: khóa là ip:&lt;địa chỉ client&gt;, ưu tiên giá trị đầu
 *       của X-Forwarded-For khi đứng sau reverse proxy (Nginx) để không gộp mọi request vào một địa chỉ proxy.</li>
 *   <li>Tắt giới hạn tốc độ (gateway.rate-limit.enabled=false): trả về rỗng, RequestRateLimiter sẽ bỏ qua.</li>
 * </ul>
 */
public class UserOrIpKeyResolver implements KeyResolver {

    static final String HEADER_USER_NAME = "X-User-Name";
    static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    static final String UNKNOWN_CLIENT = "unknown";

    private final RateLimitProperties properties;

    public UserOrIpKeyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        if (!properties.enabled()) {
            return Mono.empty();
        }
        ServerHttpRequest request = exchange.getRequest();
        String user = request.getHeaders().getFirst(HEADER_USER_NAME);
        if (user != null && !user.isBlank()) {
            return Mono.just("user:" + user.trim());
        }
        return Mono.just("ip:" + clientAddress(request));
    }

    static String clientAddress(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst(HEADER_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 -> lấy địa chỉ đầu tiên (client thật)
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return UNKNOWN_CLIENT;
    }
}
