package vn.wms.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.MockAiClient;
import vn.wms.common.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Khởi động context thật ở chế độ giả lập (không khóa, không gọi mạng). Redis giả lập trả rỗng nên hạn mức cho qua
 * (fail open), đủ để chạy trọn luồng "gọi thử một câu hỏi".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiServiceApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiClient aiClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Test
    void mockModeUsesMockClient() throws Exception {
        assertThat(aiClient).isInstanceOf(MockAiClient.class);
        mockMvc.perform(get("/api/ai/health").header(TenantContext.HEADER_COMPANY_ID, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("MOCK"));
    }

    @Test
    void askInMockModeReturnsCannedVietnameseAnswer() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .header(TenantContext.HEADER_COMPANY_ID, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Tồn kho SKU-001 còn bao nhiêu?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(containsString("Tồn kho SKU-001 còn bao nhiêu?")))
                .andExpect(jsonPath("$.data.truncated").value(false));
    }

    @Test
    void livenessProbeIsAvailableWithoutCompany() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
