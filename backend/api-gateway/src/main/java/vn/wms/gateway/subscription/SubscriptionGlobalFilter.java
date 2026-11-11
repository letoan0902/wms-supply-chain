package vn.wms.gateway.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bộ lọc thuê bao (WMS-23.2), chạy ngay sau {@code JwtAuthenticationGlobalFilter}:
 *
 * <ul>
 *   <li>Request có header X-Company-Id (do bộ lọc JWT gắn từ token): đọc hash {@code wms:subscription:{companyId}}
 *       do auth-service ghi và gắn header X-Plan bằng trường {@code plan}.</li>
 *   <li>Trường {@code status} là {@code EXPIRED}: request POST, PUT, PATCH, DELETE nhận 403 mã
 *       {@code SUBSCRIPTION_EXPIRED}, trừ các đường dẫn trong {@code gateway.subscription.write-allowed-paths}.
 *       GET, HEAD, OPTIONS luôn đi qua (chế độ chỉ đọc).</li>
 *   <li>Không có hash hoặc Redis lỗi: cho request đi qua, không gắn X-Plan (fail open, chỉ ghi log debug).
 *       auth-service ghi lại hash ở mỗi lần đăng nhập và định kỳ, nên khoảng thiếu dữ liệu rất ngắn.</li>
 * </ul>
 *
 * <p>Vì đọc Redis ở mỗi request, gia hạn xong thì request ghi kế tiếp chạy được ngay, không cần đăng nhập lại.
 * Tên khóa và trường trùng với {@code vn.wms.common.plan.SubscriptionCacheKeys} (gateway không phụ thuộc module
 * common vì chạy WebFlux).
 */
@Component
@ConditionalOnProperty(prefix = "gateway.subscription", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionGlobalFilter implements GlobalFilter, Ordered {

    /** Chạy ngay sau JwtAuthenticationGlobalFilter (order -100), lúc X-Company-Id đã là giá trị đáng tin. */
    public static final int ORDER = -99;

    static final String HEADER_COMPANY_ID = "X-Company-Id";
    static final String HEADER_PLAN = "X-Plan";
    static final String SUBSCRIPTION_KEY_PREFIX = "wms:subscription:";
    static final String FIELD_PLAN = "plan";
    static final String FIELD_STATUS = "status";
    static final String STATUS_EXPIRED = "EXPIRED";
    static final String EXPIRED_MESSAGE =
            "Gói dịch vụ đã hết hạn, hệ thống đang ở chế độ chỉ đọc. "
                    + "Vui lòng gia hạn để tiếp tục thêm hoặc sửa dữ liệu.";

    private static final Set<HttpMethod> WRITE_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private static final Logger log = LoggerFactory.getLogger(SubscriptionGlobalFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SubscriptionProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SubscriptionGlobalFilter(ReactiveStringRedisTemplate redisTemplate, SubscriptionProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Long companyId = parseCompanyId(exchange.getRequest().getHeaders().getFirst(HEADER_COMPANY_ID));
        if (companyId == null) {
            return chain.filter(exchange);
        }
        String key = SUBSCRIPTION_KEY_PREFIX + companyId;
        return redisTemplate.<String, String>opsForHash().entries(key)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(Optional::of)
                .onErrorResume(ex -> {
                    log.debug("Không đọc được {} trên Redis, cho request đi qua: {}", key, ex.getMessage());
                    return Mono.just(Optional.empty());
                })
                .flatMap(hash -> apply(exchange, chain, key, hash.orElse(Map.of())));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private Mono<Void> apply(ServerWebExchange exchange, GatewayFilterChain chain, String key,
                             Map<String, String> hash) {
        String plan = trimToNull(hash.get(FIELD_PLAN));
        if (plan == null) {
            log.debug("Chưa có bộ nhớ đệm thuê bao {}, cho request đi qua", key);
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        if (STATUS_EXPIRED.equalsIgnoreCase(trimToNull(hash.get(FIELD_STATUS)))
                && WRITE_METHODS.contains(request.getMethod())
                && !isWriteAllowed(request.getURI().getPath())) {
            return reject(exchange);
        }
        ServerHttpRequest withPlan = request.mutate().headers(headers -> headers.set(HEADER_PLAN, plan)).build();
        return chain.filter(exchange.mutate().request(withPlan).build());
    }

    private boolean isWriteAllowed(String path) {
        return properties.writeAllowedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private static Long parseCompanyId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Thân phản hồi lỗi cùng khung với ApiResponse của module common (success, code, message). */
    private record ErrorBody(boolean success, String code, String message) {
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(new ErrorBody(false, "SUBSCRIPTION_EXPIRED", EXPIRED_MESSAGE));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
