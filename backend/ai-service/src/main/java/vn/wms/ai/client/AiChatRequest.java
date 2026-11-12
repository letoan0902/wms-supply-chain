package vn.wms.ai.client;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Một câu hỏi trong hội thoại có công cụ (trợ lý AI, WMS-24.4).
 *
 * @param systemPrompt  lời dặn cố định cộng tài liệu hướng dẫn; đứng đầu yêu cầu và đánh dấu bộ nhớ đệm prompt nên
 *                      không được chứa dữ liệu thay đổi theo người hỏi
 * @param history       các lượt trước (xen kẽ user, assistant, bắt đầu bằng user)
 * @param userMessage   câu hỏi hiện tại
 * @param tools         các công cụ chỉ đọc mô hình được gọi
 * @param maxTokens     số token đầu ra tối đa của mỗi lần gọi mô hình
 * @param maxToolRounds số vòng gọi công cụ tối đa; hết vòng thì mô hình phải trả lời bằng dữ liệu đã có
 * @param timeout       tổng thời gian cho cả câu hỏi (mọi lần gọi mô hình và công cụ)
 */
public record AiChatRequest(String systemPrompt, List<AiChatMessage> history, String userMessage,
                            List<AiTool> tools, long maxTokens, int maxToolRounds, Duration timeout) {

    public AiChatRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(timeout, "timeout");
        history = List.copyOf(Objects.requireNonNull(history, "history"));
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens phải lớn hơn 0");
        }
        if (maxToolRounds < 0) {
            throw new IllegalArgumentException("maxToolRounds không được âm");
        }
    }
}
