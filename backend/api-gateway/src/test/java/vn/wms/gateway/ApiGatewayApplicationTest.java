package vn.wms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import vn.wms.gateway.ratelimit.UserOrIpKeyResolver;
import vn.wms.gateway.security.GatewayJwtProperties;
import vn.wms.gateway.subscription.SubscriptionGlobalFilter;
import vn.wms.gateway.subscription.SubscriptionProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nạp toàn bộ context của gateway với profile test (không Config Server, không Eureka) để phát hiện sớm lỗi
 * cấu hình application.yml: route, CORS, actuator và default-filters (trong đó có RequestRateLimiter).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private KeyResolver keyResolver;

    @Autowired
    private RateLimiter<?> rateLimiter;

    @Autowired
    private GatewayJwtProperties jwtProperties;

    @Autowired
    private SubscriptionProperties subscriptionProperties;

    @Autowired
    private SubscriptionGlobalFilter subscriptionFilter;

    @Test
    void healthEndpointIsUp() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void definesRoutesForAllBusinessServices() {
        List<String> routeIds = routeLocator.getRoutes().map(route -> route.getId()).collectList().block();

        assertThat(routeIds).containsExactlyInAnyOrder(
                "auth-service", "product-service", "inventory-service", "order-service", "notification-service",
                "notification-websocket", "ai-service");
    }

    @Test
    void aiRouteForwardsToAiServiceAndRequiresLogin() {
        Route route = routeLocator.getRoutes().filter(candidate -> "ai-service".equals(candidate.getId()))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://ai-service");
        for (String path : List.of("/api/ai/health", "/api/ai/usage", "/api/ai/ask")) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
            assertThat(Mono.from(route.getPredicate().apply(exchange)).block()).as(path).isTrue();
        }
        assertThat(jwtProperties.publicPaths()).noneMatch(path -> path.startsWith("/api/ai"));
    }

    @Test
    void webSocketRouteCoversRawAndSockJsHandshakePathsAndIsPublic() {
        Route route = routeLocator.getRoutes().filter(candidate -> "notification-websocket".equals(candidate.getId()))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://notification-service");
        for (String path : List.of("/ws-wms", "/ws-wms/info", "/ws-wms/123/abcdef/websocket")) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
            assertThat(Mono.from(route.getPredicate().apply(exchange)).block()).as(path).isTrue();
        }
        // Handshake không mang được Bearer header, JWT được kiểm tra ở frame STOMP CONNECT trong notification-service
        assertThat(jwtProperties.publicPaths()).contains("/ws-wms/**");
    }

    @Test
    void allowsCorsPreflightFromFrontendDevServer() {
        webTestClient.options().uri("/api/products")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
    }

    @Test
    void registersRateLimiterBeansFromConfiguration() {
        assertThat(keyResolver).isInstanceOf(UserOrIpKeyResolver.class);
        assertThat(rateLimiter).isNotNull();
    }

    @Test
    void registersSubscriptionFilterWithWriteExceptionsFromConfiguration() {
        assertThat(subscriptionFilter.getOrder()).isEqualTo(SubscriptionGlobalFilter.ORDER);
        assertThat(subscriptionProperties.enabled()).isTrue();
        assertThat(subscriptionProperties.writeAllowedPaths()).contains(
                "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/users/change-password",
                "/api/auth/subscription/**", "/api/auth/payments/**");
    }
}
