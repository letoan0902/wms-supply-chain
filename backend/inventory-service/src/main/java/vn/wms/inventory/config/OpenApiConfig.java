package vn.wms.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mô tả OpenAPI của Inventory Service (xem docs/ARCHITECTURE.md, mục tài liệu API).
 *
 * <p>Địa chỉ máy chủ trong mô tả là địa chỉ api-gateway (biến môi trường {@code GATEWAY_PUBLIC_URL},
 * mặc định http://localhost:8080) để nút "Try it out" gọi qua gateway, nơi kiểm tra JWT và gắn header doanh nghiệp,
 * chứ không gọi thẳng tên máy nội bộ của service.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String API_KEY_SCHEME = "apiKeyAuth";

    @Bean
    OpenAPI wmsOpenApi(@Value("${GATEWAY_PUBLIC_URL:http://localhost:8080}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("WMS Inventory Service API")
                        .version("v1")
                        .description("Kho, vị trí lưu trữ, tồn kho, lô, điều chuyển, kiểm kê và phiếu lấy hàng. Gọi qua api-gateway với header Authorization: Bearer <JWT> "
                                + "(lấy từ POST /api/auth/login) hoặc, cho tích hợp chỉ đọc, header X-Api-Key."))
                .addServersItem(new Server().url(serverUrl).description("api-gateway"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token do auth-service cấp khi đăng nhập; gửi qua header "
                                        + "Authorization: Bearer <JWT>."))
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")
                                .description("API key của doanh nghiệp cho tích hợp chỉ đọc (vai trò INTEGRATION); "
                                        + "thay cho JWT, chỉ gọi được các API GET.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
