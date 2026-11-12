package vn.wms.gateway.apikey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.wms.gateway.security.GatewayJwtProperties;
import vn.wms.gateway.security.JwtAuthenticationGlobalFilter;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticationGlobalFilterTest {

    /** Key giả có đúng dạng do auth-service sinh (wms_ cộng 43 ký tự base64url), chỉ dùng trong test. */
    private static final String API_KEY = "wms_" + "A".repeat(20) + "b".repeat(20) + "-_9";
    private static final String LOOKUP_KEY = "wms:apikey:" + ApiKeyAuthenticationGlobalFilter.sha256Hex(API_KEY);
    private static final String SUBSCRIPTION_KEY = "wms:subscription:7";
    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final String RATE_KEY = "wms:apikey:rate:12:" + NOW.getEpochSecond();
    private static final List<String> ALLOWED_PATHS = List.of("/api/products/**", "/api/inventory/**", "/api/orders/**");
    private static final String JWT_SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveHashOperations<String, Object, Object> hashOperations;
    private ReactiveValueOperations<String, String> valueOperations;
    private ApiKeyAuthenticationGlobalFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        hashOperations = mock(ReactiveHashOperations.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries(anyString())).thenReturn(Flux.empty());
        when(hashOperations.put(anyString(), any(), any())).thenReturn(Mono.just(true));
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        filter = filter(new ApiKeyProperties(true, 20, ALLOWED_PATHS));
    }

    private ApiKeyAuthenticationGlobalFilter filter(ApiKeyProperties properties) {
        return new ApiKeyAuthenticationGlobalFilter(redisTemplate, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenKey(String companyId, String keyId) {
        Map<Object, Object> hash = Map.of("companyId", companyId, "keyId", keyId, "prefix", API_KEY.substring(0, 8));
        when(hashOperations.entries(LOOKUP_KEY)).thenReturn(Flux.fromIterable(hash.entrySet()));
    }

    private void givenSubscription(String plan, String status, String apiAccess) {
        Map<Object, Object> hash = Map.of("plan", plan, "status", status, "apiAccess", apiAccess);
        when(hashOperations.entries(SUBSCRIPTION_KEY)).thenReturn(Flux.fromIterable(hash.entrySet()));
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, String apiKey) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path)
                .header("X-User-Name", "ke-gia-mao")
                .header("X-User-Roles", "ADMIN")
                .header("X-User-Warehouse-Ids", "99")
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .header("X-Company-Id", "999")
                .header("X-Plan", "PRO")
                .header("X-Warehouse-Ids", "5");
        if (apiKey != null) {
            builder.header("X-Api-Key", apiKey);
        }
        return MockServerWebExchange.from(builder);
    }

    private static JsonNode body(MockServerWebExchange exchange) throws Exception {
        return OBJECT_MAPPER.readTree(exchange.getResponse().getBodyAsString().block());
    }

    private void assertRejected(MockServerWebExchange exchange, HttpStatus status, String code) throws Exception {
        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
        JsonNode body = body(exchange);
        assertThat(body.path("success").asBoolean(true)).isFalse();
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
    }

    @Test
    void validKeyForwardsCompanyIntegrationRoleAndStripsClientIdentityHeaders() {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");

        filter.filter(exchange(HttpMethod.GET, "/api/inventory/stock/atp", API_KEY), chain).block();

        assertThat(forwarded.get()).isNotNull();
        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get("X-Company-Id")).containsExactly("7");
        assertThat(headers.get("X-User-Roles")).containsExactly("INTEGRATION");
        assertThat(headers.get("X-User-Name")).containsExactly("apikey-12");
        for (String stripped : List.of("X-User-Warehouse-Ids", "X-User-Id", "X-User-Role", "X-Plan",
                "X-Warehouse-Ids", "X-Api-Key")) {
            assertThat(headers.containsKey(stripped)).as(stripped).isFalse();
        }
        assertThat(forwarded.get().<Boolean>getAttribute(ApiKeyAuthenticationGlobalFilter.AUTHENTICATED_ATTRIBUTE))
                .isTrue();
        // Lần dùng gần nhất ghi lên Redis (epoch mili giây), không ghi cơ sở dữ liệu
        verify(hashOperations).put("wms:apikey:last-used", "12", String.valueOf(NOW.toEpochMilli()));
    }

    @Test
    void readsProductsAndOrdersOfTheKeysCompany() {
        givenKey("7", "12");
        givenSubscription("TRIAL", "ACTIVE", "true");
        for (String path : List.of("/api/products", "/api/products/5", "/api/orders/sales", "/api/orders/purchase/3")) {
            forwarded.set(null);
            filter.filter(exchange(HttpMethod.GET, path, API_KEY), chain).block();
            assertThat(forwarded.get()).as(path).isNotNull();
            assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Company-Id")).isEqualTo("7");
        }
    }

    @Test
    void unknownKeyIsRejectedWith401() throws Exception {
        // Không có hash wms:apikey:{sha256} trên Redis
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);

        filter.filter(exchange, chain).block();

        assertRejected(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    void revokedKeyIsRejectedWith401() throws Exception {
        // Thu hồi: auth-service xóa hash sau commit, lần đọc kế tiếp không còn khóa
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");
        filter.filter(exchange(HttpMethod.GET, "/api/products", API_KEY), chain).block();
        assertThat(forwarded.get()).isNotNull();

        forwarded.set(null);
        when(hashOperations.entries(LOOKUP_KEY)).thenReturn(Flux.empty());
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);
        filter.filter(exchange, chain).block();

        assertRejected(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertThat(body(exchange).path("message").asText()).contains("thu hồi");
    }

    @Test
    void malformedKeyIsRejectedWithoutReadingRedis() throws Exception {
        for (String bad : List.of("abc", "wms_short", "Bearer xyz", "wms_" + "x".repeat(300), "wms_có-dấu-tiếng-việt-dài")) {
            forwarded.set(null);
            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", bad);
            filter.filter(exchange, chain).block();
            assertRejected(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void writeRequestsAreRejectedWith403() throws Exception {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            forwarded.set(null);
            MockServerWebExchange exchange = exchange(method, "/api/products", API_KEY);

            filter.filter(exchange, chain).block();

            assertRejected(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    @Test
    void pathsOutsideProductsStockAndOrdersAreRejectedWith403() throws Exception {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");
        for (String path : List.of("/api/auth/users", "/api/auth/api-keys", "/api/notifications", "/api/ai/usage",
                "/actuator/health", "/api/productsx")) {
            forwarded.set(null);
            MockServerWebExchange exchange = exchange(HttpMethod.GET, path, API_KEY);

            filter.filter(exchange, chain).block();

            assertRejected(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    @Test
    void expiredSubscriptionIsRejectedWith403() throws Exception {
        givenKey("7", "12");
        givenSubscription("PRO", "EXPIRED", "true");
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);

        filter.filter(exchange, chain).block();

        assertRejected(exchange, HttpStatus.FORBIDDEN, "SUBSCRIPTION_EXPIRED");
    }

    @Test
    void planWithoutApiAccessIsRejectedWithPlanLimitExceeded() throws Exception {
        givenKey("7", "12");
        givenSubscription("FREE", "ACTIVE", "false");
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);

        filter.filter(exchange, chain).block();

        assertRejected(exchange, HttpStatus.FORBIDDEN, "PLAN_LIMIT_EXCEEDED");
        JsonNode data = body(exchange).path("data");
        assertThat(data.path("limit").asText()).isEqualTo("API_ACCESS");
        assertThat(data.path("plan").asText()).isEqualTo("FREE");
    }

    @Test
    void missingSubscriptionCachePassesFailOpen() {
        givenKey("7", "12");
        // Không có hash wms:subscription:7 (Redis vừa khởi động lại): cho qua như SubscriptionGlobalFilter

        filter.filter(exchange(HttpMethod.GET, "/api/products", API_KEY), chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void subscriptionRedisErrorPassesFailOpen() {
        givenKey("7", "12");
        when(hashOperations.entries(SUBSCRIPTION_KEY))
                .thenReturn(Flux.error(new RedisConnectionFailureException("Redis không chạy")));

        filter.filter(exchange(HttpMethod.GET, "/api/products", API_KEY), chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void keyLookupRedisErrorIsRejectedWith503() throws Exception {
        when(hashOperations.entries(LOOKUP_KEY))
                .thenReturn(Flux.error(new RedisConnectionFailureException("Redis không chạy")));
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);

        filter.filter(exchange, chain).block();

        assertRejected(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }

    @Test
    void requestsOverThePerSecondLimitGet429() throws Exception {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");

        when(valueOperations.increment(RATE_KEY)).thenReturn(Mono.just(20L));
        filter.filter(exchange(HttpMethod.GET, "/api/products", API_KEY), chain).block();
        assertThat(forwarded.get()).as("request thứ 20 trong giây").isNotNull();

        forwarded.set(null);
        when(valueOperations.increment(RATE_KEY)).thenReturn(Mono.just(21L));
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);
        filter.filter(exchange, chain).block();

        assertRejected(exchange, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    @Test
    void firstRequestOfASecondSetsCounterExpiry() {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");

        filter.filter(exchange(HttpMethod.GET, "/api/products", API_KEY), chain).block();

        verify(redisTemplate).expire(eq(RATE_KEY), eq(Duration.ofSeconds(2)));
    }

    @Test
    void rateLimitIsConfigurable() throws Exception {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");
        when(valueOperations.increment(RATE_KEY)).thenReturn(Mono.just(4L));
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);

        filter(new ApiKeyProperties(true, 3, ALLOWED_PATHS)).filter(exchange, chain).block();

        assertRejected(exchange, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
    }

    @Test
    void rateCounterRedisErrorPasses() {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");
        when(valueOperations.increment(anyString()))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Redis không chạy")));

        filter.filter(exchange(HttpMethod.GET, "/api/products", API_KEY), chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void requestWithoutKeyPassesUntouchedWithoutReadingRedis() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/inventory/warehouses", null);

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isSameAs(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void disabledFilterIgnoresKey() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products", API_KEY);

        filter(new ApiKeyProperties(false, 20, ALLOWED_PATHS)).filter(exchange, chain).block();

        assertThat(forwarded.get()).isSameAs(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void requestWithBothHeadersIsTreatedAsJwt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/products")
                .header("Authorization", "Bearer abc")
                .header("X-Api-Key", API_KEY));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer abc");
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Api-Key")).isFalse();
        assertThat(forwarded.get().<Boolean>getAttribute(ApiKeyAuthenticationGlobalFilter.AUTHENTICATED_ATTRIBUTE))
                .isNull();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void jwtFilterSkipsKeyAuthenticatedRequestsButStillValidatesJwtRequests() {
        givenKey("7", "12");
        givenSubscription("PRO", "ACTIVE", "true");
        JwtAuthenticationGlobalFilter jwtFilter = new JwtAuthenticationGlobalFilter(new GatewayJwtProperties(
                true, JWT_SECRET, List.of("/api/auth/login"), true));
        // Chuỗi thật: bộ lọc API key (-101) rồi bộ lọc JWT (-100) đang bật
        GatewayFilterChain keyThenJwt = exchange -> jwtFilter.filter(exchange, chain);

        filter.filter(exchange(HttpMethod.GET, "/api/products", API_KEY), keyThenJwt).block();

        assertThat(forwarded.get()).as("request bằng API key không bị bộ lọc JWT trả 401").isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("INTEGRATION");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Company-Id")).isEqualTo("7");

        // Có cả Bearer token hợp lệ và key: danh tính lấy từ JWT, không phải INTEGRATION
        forwarded.set(null);
        String token = Jwts.builder().subject("manager01").claim("roles", List.of("WAREHOUSE_MANAGER"))
                .claim("warehouseIds", List.of(1L)).claim("companyId", 3L)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        MockServerWebExchange both = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header("Authorization", "Bearer " + token)
                .header("X-Api-Key", API_KEY));
        filter.filter(both, keyThenJwt).block();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("manager01");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("WAREHOUSE_MANAGER");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Company-Id")).isEqualTo("3");

        // Có cả hai nhưng token sai: bộ lọc JWT trả 401 như cũ
        forwarded.set(null);
        MockServerWebExchange badToken = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header("Authorization", "Bearer sai")
                .header("X-Api-Key", API_KEY));
        filter.filter(badToken, keyThenJwt).block();
        assertThat(forwarded.get()).isNull();
        assertThat(badToken.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void runsRightBeforeJwtFilter() {
        assertThat(filter.getOrder()).isEqualTo(-101);
        JwtAuthenticationGlobalFilter jwtFilter = new JwtAuthenticationGlobalFilter(
                new GatewayJwtProperties(false, JWT_SECRET, List.of(), true));
        assertThat(filter.getOrder()).isLessThan(jwtFilter.getOrder());
    }

    @Test
    void hashMatchesTheFormatAuthServiceStores() {
        // SHA-256 hex chữ thường 64 ký tự (giống ApiKeyService.sha256Hex của auth-service)
        assertThat(ApiKeyAuthenticationGlobalFilter.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
