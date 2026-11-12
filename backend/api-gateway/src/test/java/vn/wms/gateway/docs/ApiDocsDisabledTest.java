package vn.wms.gateway.docs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** SWAGGER_ENABLED=false (production): không có route tài liệu và không có giao diện Swagger. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gateway.swagger.enabled=false", "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"})
@ActiveProfiles("test")
class ApiDocsDisabledTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void noDocsRoutesWhenDisabled() {
        var routeIds = routeLocator.getRoutes().map(route -> route.getId()).collectList().block();

        assertThat(routeIds).noneMatch(id -> id.startsWith("docs-"));
    }

    @Test
    void docsEndpointsAreNotPublished() {
        webTestClient.get().uri("/v3/api-docs/auth").exchange().expectStatus().isNotFound();
        webTestClient.get().uri("/swagger-ui.html").exchange().expectStatus().isNotFound();
    }
}
