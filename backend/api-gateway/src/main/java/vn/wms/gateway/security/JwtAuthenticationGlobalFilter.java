package vn.wms.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bộ lọc toàn cục kiểm tra JWT trước khi chuyển request tới service phía sau.
 *
 * <ul>
 *   <li>Khi gateway.jwt.enabled=false (mặc định của giai đoạn khởi tạo): chỉ chuyển tiếp request.</li>
 *   <li>Khi bật: yêu cầu header Authorization dạng Bearer có token hợp lệ (chữ ký HS256 và chưa hết hạn),
 *       trừ các đường dẫn công khai và request preflight CORS (OPTIONS). Sai hoặc thiếu token trả 401.</li>
 *   <li>Token hợp lệ: gateway thêm header X-User-Name và X-User-Roles để service phía sau biết người gọi.</li>
 *   <li>Luôn gỡ hai header X-User-* do client tự gửi để không ai giả mạo được danh tính.</li>
 * </ul>
 *
 * <p>TODO (Sprint 1): kiểm tra quyền theo vai trò ở mức route (ví dụ chỉ ADMIN được gọi API quản lý người dùng).
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    static final String HEADER_USER_NAME = "X-User-Name";
    static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GatewayJwtProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final SecretKey signingKey;

    public JwtAuthenticationGlobalFilter(GatewayJwtProperties properties) {
        this.properties = properties;
        if (properties.enabled()) {
            byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "gateway.jwt.secret (JWT_SECRET) phải dài tối thiểu 32 byte (256 bit) cho thuật toán HS256");
            }
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } else {
            this.signingKey = null;
            log.warn("Kiểm tra JWT tại gateway đang TẮT (gateway.jwt.enabled=false) "
                    + "- chỉ dùng cho giai đoạn phát triển");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_NAME);
                    headers.remove(HEADER_USER_ROLES);
                })
                .build();

        if (!properties.enabled()
                || HttpMethod.OPTIONS.equals(request.getMethod())
                || isPublicPath(request.getURI().getPath())) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return reject(exchange, "Thiếu token xác thực (header Authorization dạng Bearer)");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()))
                    .getPayload();
            ServerHttpRequest authenticated = sanitized.mutate()
                    .header(HEADER_USER_NAME, claims.getSubject())
                    .header(HEADER_USER_ROLES, rolesOf(claims))
                    .build();
            return chain.filter(exchange.mutate().request(authenticated).build());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Từ chối token không hợp lệ: {}", ex.getMessage());
            return reject(exchange, "Token không hợp lệ hoặc đã hết hạn");
        }
    }

    @Override
    public int getOrder() {
        // Chạy trước các filter định tuyến của Spring Cloud Gateway
        return -100;
    }

    private boolean isPublicPath(String path) {
        return properties.publicPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private static String rolesOf(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) {
            return "";
        }
        return roles.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Thân phản hồi lỗi cùng khung với ApiResponse của module common (success, code, message). */
    private record ErrorBody(boolean success, String code, String message) {
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(new ErrorBody(false, "UNAUTHORIZED", message));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
