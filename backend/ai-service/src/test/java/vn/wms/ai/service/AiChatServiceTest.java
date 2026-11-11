package vn.wms.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.ai.assistant.AssistantLookups;
import vn.wms.ai.assistant.AssistantTools;
import vn.wms.ai.assistant.ConversationStore;
import vn.wms.ai.assistant.MockAssistant;
import vn.wms.ai.assistant.UserGuide;
import vn.wms.ai.client.AiChatMessage;
import vn.wms.ai.client.AiChatReply;
import vn.wms.ai.client.AiChatRequest;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AiToolExecutor;
import vn.wms.ai.config.AiAssistantProperties;
import vn.wms.ai.config.AiMode;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.ChatRequest;
import vn.wms.ai.dto.ChatResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Đếm lượt, lịch sử hội thoại và đường đi LIVE, MOCK của {@link AiChatService}. */
class AiChatServiceTest {

    private static final AiAssistantProperties PROPERTIES = new AiAssistantProperties(2048, Duration.ofSeconds(90),
            5, Duration.ofHours(1), 10, 4000, 20);
    private static final String CONVERSATION_ID = "3f1c2b9e-1111-4a2b-9c3d-0123456789ab";

    private final AiClient aiClient = mock(AiClient.class);
    private final AiQuotaService quotaService = mock(AiQuotaService.class);
    private final ConversationStore conversations = mock(ConversationStore.class);
    private final AssistantTools tools = new AssistantTools(mock(AssistantLookups.class), new ObjectMapper());
    private final MockAssistant mockAssistant = mock(MockAssistant.class);
    private final ConversationStore.Conversation existing = new ConversationStore.Conversation(CONVERSATION_ID,
            List.of(AiChatMessage.user("Câu trước"), AiChatMessage.assistant("Đáp trước")), false);

    private AiChatService service(AiMode mode) {
        return new AiChatService(new AiRuntime(mode, "claude-sonnet-5-5"), aiClient, quotaService, conversations,
                tools, mockAssistant, new UserGuide(), PROPERTIES);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(7L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_NAME, "kho_01");
        request.addHeader(UserContext.HEADER_USER_ROLES, "WAREHOUSE_STAFF");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(conversations.open(7L, "kho_01", CONVERSATION_ID)).thenReturn(existing);
        when(quotaService.currentMax()).thenReturn(500L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void liveAnswerSendsHistoryGuideAndToolsCountsOnceAndStoresExchange() {
        when(aiClient.chat(any(), any())).thenReturn(AiChatReply.answered("SP001 còn 12 thùng.", "end_turn",
                900, 40, false, List.of("get_stock", "get_stock")));
        when(quotaService.recordAnswer(false)).thenReturn(8L);

        ChatResponse response = service(AiMode.LIVE).chat(new ChatRequest("  Tồn SP001?  ", CONVERSATION_ID));

        assertThat(response.answer()).isEqualTo("SP001 còn 12 thùng.");
        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.newConversation()).isFalse();
        assertThat(response.mode()).isEqualTo("LIVE");
        assertThat(response.lookups()).containsExactly("get_stock");
        assertThat(response.usage().used()).isEqualTo(8L);
        assertThat(response.usage().max()).isEqualTo(500L);

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiClient).chat(captor.capture(), eq(tools));
        AiChatRequest sent = captor.getValue();
        assertThat(sent.userMessage()).isEqualTo("Tồn SP001?");
        assertThat(sent.history()).isEqualTo(existing.history());
        assertThat(sent.systemPrompt()).contains("<user_guide>").contains("Tạo kho và vị trí lưu trữ")
                .doesNotContain("kho_01");
        // Lời dặn giống hệt nhau giữa mọi người hỏi (bộ nhớ đệm prompt dùng lại được)
        assertThat(sent.systemPrompt()).isEqualTo(
                vn.wms.ai.assistant.AssistantPrompts.systemPrompt(new UserGuide()));
        assertThat(sent.tools()).isEqualTo(tools.definitions());
        assertThat(sent.maxToolRounds()).isEqualTo(5);
        assertThat(sent.timeout()).isEqualTo(Duration.ofSeconds(90));
        verify(quotaService).checkQuota();
        verify(quotaService, times(1)).recordAnswer(false);
        verify(conversations).append(7L, "kho_01", existing, "Tồn SP001?", "SP001 còn 12 thùng.");
    }

    @Test
    void overQuotaDoesNotCallTheModel() {
        doThrow(new ApiException(ErrorCode.PLAN_LIMIT_EXCEEDED)).when(quotaService).checkQuota();

        assertThatThrownBy(() -> service(AiMode.LIVE).chat(new ChatRequest("Câu hỏi", null)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(aiClient);
        verify(quotaService, never()).recordAnswer(anyBoolean());
    }

    @Test
    void failureIsNotCountedAndNothingIsStored() {
        when(aiClient.chat(any(), any())).thenThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI lỗi"));

        assertThatThrownBy(() -> service(AiMode.LIVE).chat(new ChatRequest("Câu hỏi", CONVERSATION_ID)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        verify(quotaService, never()).recordAnswer(anyBoolean());
        verify(conversations, never()).append(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void refusalIsNotCountedAndNotStored() {
        when(aiClient.chat(any(), any())).thenReturn(AiChatReply.refusal("refusal", 50, 0, List.of()));
        when(quotaService.recordAnswer(true)).thenReturn(7L);

        ChatResponse response = service(AiMode.LIVE).chat(new ChatRequest("Câu hỏi", CONVERSATION_ID));

        assertThat(response.refused()).isTrue();
        verify(quotaService).recordAnswer(true);
        verify(conversations, never()).append(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void mockModeUsesRuleBasedAssistantAndNeverTheModel() {
        ConversationStore.Conversation fresh = new ConversationStore.Conversation("new-id", List.of(), true);
        when(conversations.open(7L, "kho_01", null)).thenReturn(fresh);
        when(mockAssistant.answer("Tồn SP001?")).thenReturn(
                new MockAssistant.Answer(MockAssistant.MOCK_NOTICE + "\n\nSP001 ...", AssistantTools.GET_STOCK));
        when(quotaService.recordAnswer(false)).thenReturn(1L);

        ChatResponse response = service(AiMode.MOCK).chat(new ChatRequest("Tồn SP001?", null));

        assertThat(response.mode()).isEqualTo("MOCK");
        assertThat(response.newConversation()).isTrue();
        assertThat(response.conversationId()).isEqualTo("new-id");
        assertThat(response.lookups()).containsExactly(AssistantTools.GET_STOCK);
        verify(aiClient, never()).chat(any(AiChatRequest.class), any(AiToolExecutor.class));
        verify(conversations).append(eq(7L), eq("kho_01"), eq(fresh), eq("Tồn SP001?"), anyString());
    }
}
