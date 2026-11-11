package vn.wms.ai.service;

import org.springframework.stereotype.Service;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AiReply;
import vn.wms.ai.client.AiRequest;
import vn.wms.ai.config.AiProperties;
import vn.wms.ai.dto.AskResponse;

/**
 * Hỏi đáp một câu với Claude ({@code POST /api/ai/ask}): kiểm tra hạn mức, gọi mô hình, rồi mới trừ lượt. Gọi mô hình
 * lỗi (ném {@code ApiException}) thì bước trừ lượt không chạy, nên lượt lỗi không bị tính. Trợ lý có lịch sử hội thoại,
 * tài liệu hướng dẫn và công cụ tra cứu (WMS-24.4) nằm ở {@link AiChatService}.
 */
@Service
public class AiAskService {

    private final AiClient aiClient;
    private final AiQuotaService quotaService;
    private final AiProperties properties;

    public AiAskService(AiClient aiClient, AiQuotaService quotaService, AiProperties properties) {
        this.aiClient = aiClient;
        this.quotaService = quotaService;
        this.properties = properties;
    }

    public AskResponse ask(String question) {
        quotaService.checkQuota();
        AiReply reply = aiClient.ask(new AiRequest(AiPrompts.ASSISTANT_SYSTEM_PROMPT, question.strip(),
                properties.maxTokens()));
        long used = quotaService.recordSuccess(reply);
        return new AskResponse(reply.text(), reply.truncated(), reply.refused(),
                new AskResponse.Usage(used, quotaService.currentMax()));
    }
}
