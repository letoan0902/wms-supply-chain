package vn.wms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import vn.wms.gateway.ratelimit.UserOrIpKeyResolver;

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
                "auth-service", "product-service", "inventory-service", "order-service", "notification-service");
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
}
