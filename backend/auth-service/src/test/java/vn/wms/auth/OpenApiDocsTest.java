package vn.wms.auth;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mô tả OpenAPI của auth-service công khai (không cần token) và khai báo cách gọi qua gateway. */
class OpenApiDocsTest extends BaseIntegrationTest {

    @Test
    void apiDocsArePublicAndDescribeGatewayAccess() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.apiKeyAuth.name").value("X-Api-Key"));
    }
}
