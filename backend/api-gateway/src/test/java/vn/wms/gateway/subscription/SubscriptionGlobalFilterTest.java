package vn.wms.gateway.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubscriptionGlobalFilterTest {

    private static final String KEY = "wms:subscription:7";
    /** Danh sách ngoại lệ mặc định, giống application.yml. */
    private static final List<String> WRITE_ALLOWED_PATHS = List.of(
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/users/change-password",
            "/api/auth/users/change-password", "/api/auth/subscription/**", "/api/auth/payments/**");

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveHashOperations<String, Object, Object> hashOperations;
    private SubscriptionGlobalFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        hashOperations = mock(ReactiveHashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        filter = new SubscriptionGlobalFilter(redisTemplate, new SubscriptionProperties(true, WRITE_ALLOWED_PATHS));
    }

    private void givenSubscription(String plan, String status) {
        Map<Object, Object> hash = Map.of("plan", plan, "status", status, "maxWarehouses", "1");
        when(hashOperations.entries(KEY)).thenReturn(Flux.fromIterable(hash.entrySet()));
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, String companyId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path);
        if (companyId != null) {
            builder.header("X-Company-Id", companyId);
        }
        return MockServerWebExchange.from(builder);
    }

    @Test
    void expiredSubscriptionBlocksWriteRequestWithSubscriptionExpired() {
        givenSubscription("TRIAL", "EXPIRED");
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            forwarded.set(null);
            MockServerWebExchange exchange = exchange(method, "/api/inventory/warehouses", "7");

            filter.filter(exchange, chain).block();

            assertThat(forwarded.get()).as(method.name()).isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exchange.getResponse().getBodyAsString().block())
                    .contains("\"success\":false")
                    .contains("\"code\":\"SUBSCRIPTION_EXPIRED\"")
                    .contains("chế độ chỉ đọc");
        }
    }

    @Test
    void expiredSubscriptionStillAllowsReadRequests() {
        givenSubscription("TRIAL", "EXPIRED");
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS)) {
            forwarded.set(null);

            filter.filter(exchange(method, "/api/inventory/warehouses", "7"), chain).block();

            assertThat(forwarded.get()).as(method.name()).isNotNull();
            assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Plan")).isEqualTo("TRIAL");
        }
    }

    @Test
    void expiredSubscriptionAllowsWritesOnExceptionPaths() {
        givenSubscription("PRO", "EXPIRED");
        List<String> paths = List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                "/api/users/change-password", "/api/auth/users/change-password", "/api/auth/subscription",
                "/api/auth/subscription/downgrade", "/api/auth/payments", "/api/auth/payments/123/cancel");
        for (String path : paths) {
            forwarded.set(null);

            filter.filter(exchange(HttpMethod.POST, path, "7"), chain).block();

            assertThat(forwarded.get()).as(path).isNotNull();
        }
    }

    @Test
    void activeSubscriptionAddsPlanHeaderAndAllowsWrites() {
        givenSubscription("FREE", "ACTIVE");

        filter.filter(exchange(HttpMethod.POST, "/api/inventory/warehouses", "7"), chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Plan")).isEqualTo("FREE");
    }

    @Test
    void requestWithoutCompanyHeaderPassesUntouchedWithoutReadingRedis() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/auth/companies/register", null);

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isSameAs(exchange);
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Plan")).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void missingSubscriptionHashPassesWithoutPlanHeader() {
        when(hashOperations.entries(KEY)).thenReturn(Flux.empty());

        filter.filter(exchange(HttpMethod.DELETE, "/api/products/1", "7"), chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Plan")).isFalse();
    }

    @Test
    void redisErrorPassesWithoutPlanHeader() {
        when(hashOperations.entries(anyString()))
                .thenReturn(Flux.error(new RedisConnectionFailureException("Redis không chạy")));

        filter.filter(exchange(HttpMethod.POST, "/api/inventory/warehouses", "7"), chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Plan")).isFalse();
    }

    @Test
    void runsRightAfterJwtFilter() {
        assertThat(filter.getOrder()).isEqualTo(-99);
    }
}
