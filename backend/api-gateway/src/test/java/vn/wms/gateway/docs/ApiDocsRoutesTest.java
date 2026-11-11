package vn.wms.gateway.docs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;
import vn.wms.gateway.security.GatewayJwtProperties;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Route tài liệu OpenAPI tổng hợp có mặt, công khai và trỏ đúng service khi gateway.swagger.enabled=true (mặc định). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiDocsRoutesTest {

    private static final Map<String, String> EXPECTED = Map.of(
            "auth", "lb://auth-service", "products", "lb://product-service",
            "inventory", "lb://inventory-service", "orders", "lb://order-service",
            "notifications", "lb://notification-service", "ai", "lb://ai-service");

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private GatewayJwtProperties jwtProperties;

    @Test
    void definesOneDocsRoutePerServiceRewrittenToApiDocs() {
        EXPECTED.forEach((name, uri) -> {
            Route route = routeLocator.getRoutes().filter(candidate -> ("docs-" + name).equals(candidate.getId()))
                    .blockFirst();
            assertThat(route).as(name).isNotNull();
            assertThat(route.getUri().toString()).isEqualTo(uri);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/v3/api-docs/" + name));
            assertThat(Mono.from(route.getPredicate().apply(exchange)).block()).as(name).isTrue();
            assertThat(route.getFilters()).as(name).hasSize(1);
        });
    }

    @Test
    void docsPathsArePublic() {
        AntPathMatcher matcher = new AntPathMatcher();
        List<String> paths = List.of("/v3/api-docs/auth", "/v3/api-docs/swagger-config", "/swagger-ui.html",
                "/swagger-ui/index.html", "/webjars/swagger-ui/index.css");
        for (String path : paths) {
            assertThat(jwtProperties.publicPaths().stream().anyMatch(pattern -> matcher.match(pattern, path)))
                    .as(path).isTrue();
        }
    }
}
