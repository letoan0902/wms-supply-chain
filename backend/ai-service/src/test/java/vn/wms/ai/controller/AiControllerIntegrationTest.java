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
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AiReply;
import vn.wms.ai.dto.AskRequest;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;

import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API của ai-service với {@link AiClient} giả lập và Redis giả lập (cùng cách inventory-service kiểm tra giới hạn
 * gói): PlanLimitChecker thật đọc hash {@code wms:subscription:{companyId}} và bộ đếm
 * {@code wms:usage:ai:{companyId}:{yyyyMM}}.
 */
@SpringBootTest(properties = "app.plan-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiControllerIntegrationTest {

    private static final long COMPANY_ID = 701L;
    private static final long OTHER_COMPANY_ID = 702L;
    private static final String AI_KEY_PREFIX = "wms:usage:ai:701:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiClient aiClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private HashOperations<String, Object, Object> hashOperations;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void givenFreePlanWithUsage(String used) {
        when(hashOperations.entries("wms:subscription:" + COMPANY_ID))
                .thenReturn(Map.copyOf(AiTestSupport.subscription("FREE", "20")));
        when(valueOperations.get(startsWith(AI_KEY_PREFIX))).thenReturn(used);
    }

    private ResultActions ask(String question) throws Exception {
        return mockMvc.perform(post("/api/ai/ask")
                .header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AskRequest(question))));
    }

    @Test
    void health_reportsModeAndModelWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/ai/health").header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.mode").value("MOCK"))
                .andExpect(jsonPath("$.data.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }

    @Test
    void ask_returnsAnswerAndIncrementsUsageOfCallerCompanyOnly() throws Exception {
        givenFreePlanWithUsage("3");
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(4L);
        when(aiClient.ask(any())).thenReturn(
                AiReply.answered("Vào mục Kho, bấm Thêm kho.", "end_turn", 80, 15, false));

        ask("Tạo kho thế nào?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("Vào mục Kho, bấm Thêm kho."))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.refused").value(false))
                .andExpect(jsonPath("$.data.usage.used").value(4))
                .andExpect(jsonPath("$.data.usage.max").value(20));

        verify(valueOperations).increment(startsWith(AI_KEY_PREFIX));
        // Cách ly theo doanh nghiệp: bộ đếm của doanh nghiệp khác không bị đụng tới
        verify(valueOperations, never()).increment(startsWith("wms:usage:ai:" + OTHER_COMPANY_ID + ":"));
    }

    @Test
    void ask_overQuotaIsRejectedWithPlanLimitExceeded() throws Exception {
        givenFreePlanWithUsage("20");

        ask("Tạo kho thế nào?")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message")
                        .value("Gói Khởi Đầu chỉ cho phép tối đa 20 lượt AI mỗi tháng. Nâng cấp gói để dùng thêm lượt AI."))
                .andExpect(jsonPath("$.data.limit").value("AI_REQUESTS"))
                .andExpect(jsonPath("$.data.current").value(20))
                .andExpect(jsonPath("$.data.max").value(20))
                .andExpect(jsonPath("$.data.plan").value("FREE"));

        verifyNoInteractions(aiClient);
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void ask_clientFailureReturns503AndDoesNotCount() throws Exception {
        givenFreePlanWithUsage("3");
        when(aiClient.ask(any())).thenThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                "Dịch vụ AI không phản hồi trong 60 giây, lượt này không bị tính. Vui lòng thử lại."));

        ask("Tạo kho thế nào?")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void ask_refusalReturnsPoliteMessageAndDoesNotCount() throws Exception {
        givenFreePlanWithUsage("3");
        when(aiClient.ask(any())).thenReturn(AiReply.refusal("refusal", 30, 0));

        ask("Câu hỏi bị từ chối")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refused").value(true))
                .andExpect(jsonPath("$.data.answer").value(AiReply.REFUSAL_MESSAGE))
                .andExpect(jsonPath("$.data.usage.used").value(3));

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void ask_emptyQuestionIsValidationError() throws Exception {
        ask("   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.question").value("Câu hỏi không được để trống"));

        verifyNoInteractions(aiClient);
    }

    @Test
    void ask_tooLongQuestionIsValidationError() throws Exception {
        ask("a".repeat(AskRequest.MAX_QUESTION_LENGTH + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.question").value("Câu hỏi tối đa 2000 ký tự"));

        verifyNoInteractions(aiClient);
    }

    @Test
    void ask_questionOfExactlyMaxLengthIsAccepted() throws Exception {
        givenFreePlanWithUsage("0");
        when(valueOperations.increment(startsWith(AI_KEY_PREFIX))).thenReturn(1L);
        when(aiClient.ask(any())).thenReturn(AiReply.answered("OK", "end_turn", 1, 1, false));

        ask("a".repeat(AskRequest.MAX_QUESTION_LENGTH)).andExpect(status().isOk());
    }

    @Test
    void missingCompanyIsRejectedWithCompanyRequired() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Tạo kho thế nào?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_REQUIRED"));
        mockMvc.perform(get("/api/ai/usage"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_REQUIRED"));

        verifyNoInteractions(aiClient);
    }

    @Test
    void usage_reportsUsedMaxMonthAndPlan() throws Exception {
        givenFreePlanWithUsage("7");

        mockMvc.perform(get("/api/ai/usage").header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(7))
                .andExpect(jsonPath("$.data.max").value(20))
                .andExpect(jsonPath("$.data.month").value(matchesPattern("\\d{4}-\\d{2}")))
                .andExpect(jsonPath("$.data.plan").value("FREE"));
    }

    @Test
    void usage_withoutSubscriptionCacheReturnsNullMaxAndPlan() throws Exception {
        when(hashOperations.entries(anyString())).thenReturn(Map.of());

        mockMvc.perform(get("/api/ai/usage").header(TenantContext.HEADER_COMPANY_ID, COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(0))
                .andExpect(jsonPath("$.data.max").value(nullValue()))
                .andExpect(jsonPath("$.data.plan").value(nullValue()));
    }
}
