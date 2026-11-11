package vn.wms.gateway.apikey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Xác thực hệ thống ngoài bằng API key (WMS-25.1, docs/SAAS_SPEC.md mục WMS-25.1). Chạy trước
 * {@code JwtAuthenticationGlobalFilter}.
 *
 * <p>Chỉ xử lý request có header {@code X-Api-Key} và KHÔNG có {@code Authorization}; request có cả hai được coi là
 * request JWT (header {@code X-Api-Key} bị gỡ, bộ lọc JWT xử lý như cũ). Request không có key đi qua nguyên vẹn.
 *
 * <ol>
 *   <li>Tính SHA-256 (hex chữ thường) của key, đọc hash Redis {@code wms:apikey:{sha256}} do auth-service ghi
 *       ({@code companyId}, {@code keyId}). Key sai dạng, không có trên Redis hoặc đã thu hồi (auth-service xóa khóa
 *       khi thu hồi): 401 {@code UNAUTHORIZED}. Không đọc được Redis: 503 {@code SERVICE_UNAVAILABLE} (không thể xác
 *       thực thì không cho qua).</li>
 *   <li>Chỉ {@code GET} trên {@code gateway.api-key.allowed-paths} (mặc định hàng hóa, tồn kho, đơn hàng); còn lại
 *       403 {@code FORBIDDEN}.</li>
 *   <li>Đọc hash thuê bao {@code wms:subscription:{companyId}}: {@code status = EXPIRED} trả 403
 *       {@code SUBSCRIPTION_EXPIRED}; {@code apiAccess} khác {@code true} (gói FREE) trả 403
 *       {@code PLAN_LIMIT_EXCEEDED} với {@code data.limit = API_ACCESS}. Thiếu hash hoặc Redis lỗi thì cho qua
 *       (fail open), cùng quy tắc với {@code SubscriptionGlobalFilter} và docs/SAAS_SPEC.md mục 2.7.</li>
 *   <li>Giới hạn tốc độ theo key: bộ đếm {@code wms:apikey:rate:{keyId}:{epochSecond}} (INCR, hết hạn sau 2 giây);
 *       vượt {@code gateway.api-key.rate-limit-per-second} (mặc định 20) trả 429 {@code TOO_MANY_REQUESTS} kèm
 *       {@code Retry-After: 1}. Redis lỗi thì cho qua.</li>
 *   <li>Ghi thời điểm dùng vào hash {@code wms:apikey:last-used} (trường {@code keyId}, epoch mili giây); auth-service
 *       đổ vào cơ sở dữ liệu mỗi phút. Lỗi khi ghi bị bỏ qua.</li>
 *   <li>Gỡ mọi header {@code X-User-*}, {@code X-Company-Id}, {@code X-Plan}, {@code X-Warehouse-Ids} và chính
 *       {@code X-Api-Key} do client gửi, rồi gắn {@code X-Company-Id} của key, {@code X-User-Roles: INTEGRATION},
 *       {@code X-User-Name: apikey-{keyId}}; đánh dấu exchange để bộ lọc JWT bỏ qua.</li>
 * </ol>
 *
 * <p>Thân phản hồi lỗi cùng khung với ApiResponse của module common ({@code success}, {@code code},
 * {@code message}, thêm {@code data} khi có). Key gốc không bao giờ được ghi log hay chuyển tiếp xuống service.
 * Tên khóa Redis trùng {@code vn.wms.common.plan.ApiKeyCacheKeys} (gateway không phụ thuộc module common).
 */
@Component
public class ApiKeyAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    /** Chạy ngay trước JwtAuthenticationGlobalFilter (order -100). */
    public static final int ORDER = -101;
    /** Thuộc tính exchange báo request đã xác thực bằng API key; bộ lọc JWT thấy thì bỏ qua. */
    public static final String AUTHENTICATED_ATTRIBUTE = ApiKeyAuthenticationGlobalFilter.class.getName() + ".authenticated";

    static final String HEADER_API_KEY = "X-Api-Key";
    static final String HEADER_USER_NAME = "X-User-Name";
    static final String HEADER_USER_ROLES = "X-User-Roles";
    static final String HEADER_COMPANY_ID = "X-Company-Id";
    static final String HEADER_PLAN = "X-Plan";
    static final String HEADER_WAREHOUSE_IDS = "X-Warehouse-Ids";
    static final String USER_HEADER_PREFIX = "X-User-";
    static final String ROLE_INTEGRATION = "INTEGRATION";
    /** Tên người gọi trung tính gắn vào X-User-Name, kèm ID key để truy vết (không chứa key). */
    static final String USER_NAME_PREFIX = "apikey-";

    static final String KEY_PREFIX = "wms:apikey:";
    static final String LAST_USED_KEY = "wms:apikey:last-used";
    static final String RATE_KEY_PREFIX = "wms:apikey:rate:";
    static final String FIELD_COMPANY_ID = "companyId";
    static final String FIELD_KEY_ID = "keyId";
    static final String SUBSCRIPTION_KEY_PREFIX = "wms:subscription:";
    static final String FIELD_PLAN = "plan";
    static final String FIELD_STATUS = "status";
    static final String FIELD_API_ACCESS = "apiAccess";
    static final String STATUS_EXPIRED = "EXPIRED";

    static final String INVALID_KEY_MESSAGE = "API key không hợp lệ hoặc đã bị thu hồi";
    static final String NOT_ALLOWED_MESSAGE =
            "API key chỉ được đọc dữ liệu hàng hóa, tồn kho và đơn hàng (phương thức GET)";
    static final String EXPIRED_MESSAGE =
            "Gói dịch vụ của doanh nghiệp đã hết hạn nên API key tạm ngưng hoạt động. Vui lòng gia hạn gói.";
    static final String NO_API_ACCESS_MESSAGE =
            "Gói dịch vụ hiện tại của doanh nghiệp không hỗ trợ API key. Vui lòng nâng cấp gói.";
    static final String RATE_LIMITED_MESSAGE = "API key gửi quá nhiều yêu cầu mỗi giây, vui lòng thử lại sau";
    static final String UNAVAILABLE_MESSAGE = "Chưa kiểm tra được API key, vui lòng thử lại sau";

    /** Key do auth-service sinh: {@code wms_} cộng chuỗi base64url; giới hạn độ dài để không băm chuỗi rác lớn. */
    private static final Pattern KEY_FORMAT = Pattern.compile("wms_[A-Za-z0-9_-]{20,200}");
    /** Bộ đếm theo giây giữ thêm một giây cho chắc chắn rồi tự xóa. */
    private static final Duration RATE_WINDOW_TTL = Duration.ofSeconds(2);

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationGlobalFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ApiKeyProperties properties;
    private final Clock clock;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    public ApiKeyAuthenticationGlobalFilter(ReactiveStringRedisTemplate redisTemplate, ApiKeyProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    /** Dùng trong test để cố định giây của bộ đếm tốc độ. */
    ApiKeyAuthenticationGlobalFilter(ReactiveStringRedisTemplate redisTemplate, ApiKeyProperties properties,
                                     Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String apiKey = request.getHeaders().getFirst(HEADER_API_KEY);
        if (!properties.enabled() || apiKey == null || apiKey.isBlank()) {
            return chain.filter(exchange);
        }
        if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            // Có cả JWT lẫn key: xử lý như request JWT, không chuyển tiếp key xuống service
            ServerHttpRequest withoutKey = request.mutate().headers(headers -> headers.remove(HEADER_API_KEY)).build();
            return chain.filter(exchange.mutate().request(withoutKey).build());
        }
        String key = apiKey.trim();
        if (!KEY_FORMAT.matcher(key).matches()) {
            return reject(exchange, Denial.unauthorized());
        }
        String lookupKey = KEY_PREFIX + sha256Hex(key);
        return readHash(lookupKey).flatMap(lookup -> {
            if (lookup.isEmpty()) {
                log.warn("Không đọc được khóa tra cứu API key trên Redis, từ chối request");
                return reject(exchange, new Denial(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                        UNAVAILABLE_MESSAGE, null));
            }
            KeyInfo info = KeyInfo.from(lookup.get());
            if (info == null) {
                return reject(exchange, Denial.unauthorized());
            }
            if (!HttpMethod.GET.equals(request.getMethod()) || !isAllowedPath(request.getURI().getPath())) {
                log.debug("API key ID {} gọi {} {} ngoài phạm vi cho phép", info.keyId(), request.getMethod(),
                        request.getURI().getPath());
                return reject(exchange, new Denial(HttpStatus.FORBIDDEN, "FORBIDDEN", NOT_ALLOWED_MESSAGE, null));
            }
            return checkSubscription(info.companyId()).flatMap(denial -> denial
                    .map(value -> reject(exchange, value))
                    .orElseGet(() -> withinRateLimit(info.keyId()).flatMap(allowed -> allowed
                            ? forward(exchange, chain, info)
                            : reject(exchange, new Denial(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                            RATE_LIMITED_MESSAGE, null)))));
        });
    }

    private boolean isAllowedPath(String path) {
        return properties.allowedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /** Đọc một hash; rỗng ({@code Optional.empty()}) nghĩa là Redis lỗi, map rỗng nghĩa là khóa không tồn tại. */
    private Mono<Optional<Map<String, String>>> readHash(String key) {
        return redisTemplate.<String, String>opsForHash().entries(key)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(Optional::of)
                .onErrorResume(ex -> {
                    log.debug("Không đọc được {} trên Redis: {}", key, ex.getMessage());
                    return Mono.just(Optional.empty());
                });
    }

    private Mono<Optional<Denial>> checkSubscription(long companyId) {
        String key = SUBSCRIPTION_KEY_PREFIX + companyId;
        return readHash(key).map(result -> {
            Map<String, String> hash = result.orElse(Map.of());
            String plan = trimToNull(hash.get(FIELD_PLAN));
            if (plan == null) {
                log.debug("Chưa có bộ nhớ đệm thuê bao {}, cho API key đi qua (fail open)", key);
                return Optional.empty();
            }
            if (STATUS_EXPIRED.equalsIgnoreCase(trimToNull(hash.get(FIELD_STATUS)))) {
                return Optional.of(new Denial(HttpStatus.FORBIDDEN, "SUBSCRIPTION_EXPIRED", EXPIRED_MESSAGE, null));
            }
            if (!"true".equalsIgnoreCase(trimToNull(hash.get(FIELD_API_ACCESS)))) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("limit", "API_ACCESS");
                data.put("plan", plan);
                return Optional.of(new Denial(HttpStatus.FORBIDDEN, "PLAN_LIMIT_EXCEEDED", NO_API_ACCESS_MESSAGE,
                        data));
            }
            return Optional.empty();
        });
    }

    /** Đếm request của key trong giây hiện tại; {@code false} khi vượt giới hạn. Redis lỗi thì cho qua. */
    private Mono<Boolean> withinRateLimit(long keyId) {
        int limit = properties.rateLimitPerSecond();
        if (limit < 1) {
            return Mono.just(true);
        }
        String key = RATE_KEY_PREFIX + keyId + ":" + clock.instant().getEpochSecond();
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Boolean> expire = count == 1L
                            ? redisTemplate.expire(key, RATE_WINDOW_TTL).onErrorReturn(false)
                            : Mono.just(true);
                    return expire.thenReturn(count <= limit);
                })
                .defaultIfEmpty(true)
                .onErrorResume(ex -> {
                    log.debug("Không đếm được tốc độ của API key ID {} trên Redis, cho qua: {}", keyId,
                            ex.getMessage());
                    return Mono.just(true);
                });
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, KeyInfo info) {
        ServerHttpRequest authenticated = exchange.getRequest().mutate()
                .headers(headers -> {
                    List<String> userHeaders = new ArrayList<>();
                    for (String name : headers.keySet()) {
                        if (name.regionMatches(true, 0, USER_HEADER_PREFIX, 0, USER_HEADER_PREFIX.length())) {
                            userHeaders.add(name);
                        }
                    }
                    userHeaders.forEach(headers::remove);
                    headers.remove(HEADER_COMPANY_ID);
                    headers.remove(HEADER_PLAN);
                    headers.remove(HEADER_WAREHOUSE_IDS);
                    headers.remove(HEADER_API_KEY);
                    headers.set(HEADER_USER_NAME, USER_NAME_PREFIX + info.keyId());
                    headers.set(HEADER_USER_ROLES, ROLE_INTEGRATION);
                    headers.set(HEADER_COMPANY_ID, String.valueOf(info.companyId()));
                })
                .build();
        ServerWebExchange mutated = exchange.mutate().request(authenticated).build();
        mutated.getAttributes().put(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        return recordLastUsed(info.keyId()).then(Mono.defer(() -> chain.filter(mutated)));
    }

    /** Một lệnh HSET mỗi request; auth-service đổ vào cơ sở dữ liệu định kỳ. Lỗi được bỏ qua. */
    private Mono<Void> recordLastUsed(long keyId) {
        return redisTemplate.opsForHash()
                .put(LAST_USED_KEY, String.valueOf(keyId), String.valueOf(clock.millis()))
                .onErrorResume(ex -> {
                    log.debug("Không ghi được lần dùng của API key ID {}: {}", keyId, ex.getMessage());
                    return Mono.just(false);
                })
                .then();
    }

    /** SHA-256 của key (UTF-8), hex chữ thường 64 ký tự, giống {@code ApiKeyService.sha256Hex} của auth-service. */
    static String sha256Hex(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", ex);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long positiveLong(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Doanh nghiệp và ID của key đọc từ hash tra cứu; {@code null} khi hash rỗng hoặc hỏng. */
    private record KeyInfo(long companyId, long keyId) {

        static KeyInfo from(Map<String, String> hash) {
            Long companyId = positiveLong(hash.get(FIELD_COMPANY_ID));
            Long keyId = positiveLong(hash.get(FIELD_KEY_ID));
            return companyId == null || keyId == null ? null : new KeyInfo(companyId, keyId);
        }
    }

    /** Lý do từ chối: mã HTTP, mã lỗi, thông báo và dữ liệu kèm theo (có thể null). */
    private record Denial(HttpStatus status, String code, String message, Object data) {

        static Denial unauthorized() {
            return new Denial(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", INVALID_KEY_MESSAGE, null);
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, Denial denial) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(denial.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (denial.status() == HttpStatus.TOO_MANY_REQUESTS) {
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", denial.code());
        body.put("message", denial.message());
        if (denial.data() != null) {
            body.put("data", denial.data());
        }
        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
