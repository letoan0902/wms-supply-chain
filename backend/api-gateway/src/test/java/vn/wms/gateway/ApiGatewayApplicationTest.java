package vn.wms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.gateway.ratelimit.UserOrIpKeyResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nạp toàn bộ context của gateway với profile test (không Config Server, không Eureka) để phát hiện sớm lỗi
 * cấu hình application.yml: route, default-filters (trong đó có RequestRateLimiter) và các bean giới hạn tốc độ.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private KeyResolver keyResolver;

    @Autowired
    private RateLimiter<?> rateLimiter;

    @Test
    void contextLoadsWithRoutesAndRateLimiterBeans() {
        assertThat(routeLocator.getRoutes().collectList().block()).hasSize(5);
        assertThat(keyResolver).isInstanceOf(UserOrIpKeyResolver.class);
        assertThat(rateLimiter).isNotNull();
    }
}
