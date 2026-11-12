package vn.wms.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.assistant.MockAssistant;
import vn.wms.ai.assistant.ServiceDataClient;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.dto.ChatRequest;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.time.Duration;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/ai/chat} ở chế độ giả lập (profile test) với Redis giả lập và PlanLimitChecker thật: kiểm tra dữ
 * liệu đầu vào, câu trả lời từ tài liệu hướng dẫn, lỗi 403 của service phía sau thành câu trả lời, đếm lượt và khóa
 * Redis của hội thoại theo doanh nghiệp.
 */
@SpringBootTest(properties = "app.plan-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiChatControllerIntegrationTest {

    private static final long COMPANY_ID = 701L;
    private static final String AI_KEY_PREFIX = "wms:usage:ai:701:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiClient aiClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ServiceDataClient serviceDataClient;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries("wms:subscription:" + COMPANY_ID))
                .thenReturn(Map.copyOf(AiTestSupport.subscription("TRIAL", "500")));
        when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith(AI_KEY_PREFIX))).thenReturn("3");
        when(valueOperations.increment(org.mockito.ArgumentMatchers.startsWith(AI_KEY_PREFIX))).thenReturn(4L);
    }

    private ResultActions chat(Object body) throws Exception {
        return mockMvc.perform(post("/api/ai/chat")
                .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                .header(UserContext.HEADER_USER_NAME, "ban_hang_01")
                .header(UserContext.HEADER_USER_ROLES, "SALES")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void howToQuestionIsAnsweredFromUserGuideInMockModeAndCountedOnce() throws Exception {
        chat(new ChatRequest("Tạo kho thế nào?", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer", startsWith(MockAssistant.MOCK_NOTICE)))
                .andExpect(jsonPath("$.data.answer", containsString("Tạo kho và vị trí lưu trữ")))
                .andExpect(jsonPath("$.data.conversationId", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.data.newConversation").value(true))
                .andExpect(jsonPath("$.data.mode").value("MOCK"))
                .andExpect(jsonPath("$.data.refused").value(false))
                .andExpect(jsonPath("$.data.lookups").isEmpty())
                .andExpect(jsonPath("$.data.usage.used").value(4))
                .andExpect(jsonPath("$.data.usage.max").value(500));

        verify(valueOperations).increment(org.mockito.ArgumentMatchers.startsWith(AI_KEY_PREFIX));
        // Lịch sử lưu theo doanh nghiệp và người hỏi, giữ 1 giờ
        verify(valueOperations).set(org.mockito.ArgumentMatchers.startsWith("wms:ai:chat:701:"), anyString(),
                eq(Duration.ofHours(1)));
        verifyNoInteractions(aiClient);
    }

    @Test
    void forbiddenFromTargetServiceIsAnAnswerNotAnError() throws Exception {
        when(serviceDataClient.get(eq("order-service"), anyString(), anyMap(), anyMap()))
                .thenReturn(new ServiceDataClient.Result(ServiceDataClient.Status.FORBIDDEN, null,
                        ServiceDataClient.FORBIDDEN_MESSAGE));

        chat(new ChatRequest("Đơn PO-20261110-0001 đã duyệt chưa?", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer", containsString("Bạn không có quyền xem dữ liệu này.")))
                .andExpect(jsonPath("$.data.lookups[0]").value("get_purchase_order"));
    }

    @Test
    void emptyOrTooLongMessageIsValidationErrorAndNotCounted() throws Exception {
        chat(new ChatRequest("   ", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.message").value("Câu hỏi không được để trống"));
        chat(new ChatRequest("a".repeat(ChatRequest.MAX_MESSAGE_LENGTH + 1), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.message").value("Câu hỏi tối đa 2000 ký tự"));
        chat(Map.of())
                .andExpect(status().isBadRequest());

        verify(valueOperations, never()).increment(anyString());
        verify(serviceDataClient, never()).get(any(), any(), any(), any());
    }

    @Test
    void overQuotaIsPlanLimitExceeded() throws Exception {
        when(valueOperations.get(org.mockito.ArgumentMatchers.startsWith(AI_KEY_PREFIX))).thenReturn("500");

        chat(new ChatRequest("Tạo kho thế nào?", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.data.limit").value("AI_REQUESTS"));

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void missingCompanyIsRejected() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Tạo kho thế nào?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_REQUIRED"));
    }
}
