package vn.wms.gateway.docs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Route tài liệu OpenAPI tổng hợp (WMS-21.1): {@code /v3/api-docs/auth} được viết lại thành {@code /v3/api-docs} của
 * auth-service, tương tự cho các service còn lại. Giao diện Swagger của gateway (springdoc, cấu hình trong
 * application.yml) đọc các đường dẫn này nên "Try it out" và tài liệu đều đi qua gateway.
 *
 * <p>Chỉ đăng ký khi {@code gateway.swagger.enabled=true} (biến {@code SWAGGER_ENABLED}, mặc định true). Production đặt
 * false: không có route thì /v3/api-docs/** trả 404 và springdoc cũng tắt.
 */
@Configuration
@ConditionalOnProperty(prefix = "gateway.swagger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiDocsRoutesConfig {

    /** Tiền tố id của các route tài liệu, test dùng để phân biệt với route nghiệp vụ. */
    public static final String ROUTE_ID_PREFIX = "docs-";

    /** Tên đường dẫn công khai {@code /v3/api-docs/{tên}} và service tương ứng trong Eureka. */
    static final Map<String, String> SERVICES = services();

    private static Map<String, String> services() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("auth", "auth-service");
        map.put("products", "product-service");
        map.put("inventory", "inventory-service");
        map.put("orders", "order-service");
        map.put("notifications", "notification-service");
        map.put("ai", "ai-service");
        return map;
    }

    @Bean
    RouteLocator apiDocsRoutes(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        SERVICES.forEach((name, service) -> routes.route(ROUTE_ID_PREFIX + name, route -> route
                .path("/v3/api-docs/" + name)
                .filters(filter -> filter.rewritePath("/v3/api-docs/" + name, "/v3/api-docs"))
                .uri("lb://" + service)));
        return routes.build();
    }
}
