package vn.wms.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.wms.ai.assistant.AssistantPrompts;
import vn.wms.ai.assistant.AssistantTools;
import vn.wms.ai.assistant.ConversationStore;
import vn.wms.ai.assistant.MockAssistant;
import vn.wms.ai.assistant.UserGuide;
import vn.wms.ai.client.AiChatReply;
import vn.wms.ai.client.AiChatRequest;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.config.AiAssistantProperties;
import vn.wms.ai.config.AiRuntime;
import vn.wms.ai.dto.AskResponse;
import vn.wms.ai.dto.ChatRequest;
import vn.wms.ai.dto.ChatResponse;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Trợ lý AI hỏi đáp có lịch sử ({@code POST /api/ai/chat}, WMS-24.4 gộp WMS-18.2).
 *
 * <p>Mỗi câu hỏi tính đúng một lượt AI, giống {@code POST /api/ai/ask}: kiểm tra hạn mức trước, chỉ tăng bộ đếm sau
 * khi có câu trả lời; lỗi (AI không phản hồi, hết thời gian) và câu bị từ chối không bị tính, dù trong câu hỏi đó
 * Claude đã gọi bao nhiêu lần.
 *
 * <p>Chế độ LIVE: Claude với lời dặn cố định cộng tài liệu hướng dẫn (đánh dấu bộ nhớ đệm prompt) và bộ công cụ chỉ
 * đọc {@link AssistantTools}. Chế độ MOCK: {@link MockAssistant} chạy thẳng các phép tra cứu hoặc trả mục hướng dẫn.
 * Cả hai đều tra cứu bằng danh tính người hỏi nên chỉ thấy dữ liệu của doanh nghiệp và phạm vi kho của họ.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final AiRuntime runtime;
    private final AiClient aiClient;
    private final AiQuotaService quotaService;
    private final ConversationStore conversations;
    private final AssistantTools tools;
    private final MockAssistant mockAssistant;
    private final AiAssistantProperties properties;
    private final String systemPrompt;

    public AiChatService(AiRuntime runtime, AiClient aiClient, AiQuotaService quotaService,
                         ConversationStore conversations, AssistantTools tools, MockAssistant mockAssistant,
                         UserGuide guide, AiAssistantProperties properties) {
        this.runtime = runtime;
        this.aiClient = aiClient;
        this.quotaService = quotaService;
        this.conversations = conversations;
        this.tools = tools;
        this.mockAssistant = mockAssistant;
        this.properties = properties;
        this.systemPrompt = AssistantPrompts.systemPrompt(guide);
    }

    public ChatResponse chat(ChatRequest request) {
        long companyId = TenantContext.requireCompanyId();
        String username = UserContext.getCurrentUser().map(CurrentUser::username).orElse(null);
        String message = request.message().strip();
        quotaService.checkQuota();

        ConversationStore.Conversation conversation = conversations.open(companyId, username,
                request.conversationId());
        String answer;
        boolean truncated = false;
        boolean refused = false;
        List<String> lookups = new ArrayList<>();
        if (runtime.isMock()) {
            MockAssistant.Answer mock = mockAssistant.answer(message);
            answer = mock.text();
            if (mock.lookup() != null) {
                lookups.add(mock.lookup());
            }
        } else {
            AiChatReply reply = aiClient.chat(new AiChatRequest(systemPrompt, conversation.history(), message,
                    tools.definitions(), properties.maxTokens(), properties.maxToolRounds(), properties.timeout()),
                    tools);
            answer = reply.text();
            truncated = reply.truncated();
            refused = reply.refused();
            reply.toolsUsed().stream().distinct().forEach(lookups::add);
            log.info("Trợ lý AI trả lời: {} lần tra cứu, {} token đầu vào, {} token đầu ra, stop_reason {}",
                    reply.toolsUsed().size(), reply.inputTokens(), reply.outputTokens(), reply.stopReason());
        }

        long used = quotaService.recordAnswer(refused);
        if (!refused) {
            conversations.append(companyId, username, conversation, message, answer);
        }
        return new ChatResponse(answer, conversation.id(), conversation.created(), truncated, refused,
                runtime.mode().name(), List.copyOf(lookups), new AskResponse.Usage(used, quotaService.currentMax()));
    }
}
