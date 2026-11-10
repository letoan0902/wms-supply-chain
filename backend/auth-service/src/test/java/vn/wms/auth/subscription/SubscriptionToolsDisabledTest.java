package vn.wms.auth.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import vn.wms.auth.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hai API chỉ dùng khi phát triển và trình diễn: tắt cấu hình thì trả 404.
 * PAYOS_MOCK=false (như trên máy chủ thật) và APP_DEMO_TOOLS để mặc định (false).
 */
@TestPropertySource(properties = "payos.mock=false")
class SubscriptionToolsDisabledTest extends BaseIntegrationTest {

    @Test
    @DisplayName("renew-mock trả 404 khi PAYOS_MOCK=false")
    void renewMockIsNotFoundWhenMockDisabled() throws Exception {
        String token = loginAndGetToken("admin", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/auth/subscription/renew-mock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\",\"cycle\":\"MONTHLY\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("demo/expire trả 404 khi APP_DEMO_TOOLS để mặc định")
    void demoExpireIsNotFoundByDefault() throws Exception {
        String token = loginAndGetToken("admin", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/auth/subscription/demo/expire")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
