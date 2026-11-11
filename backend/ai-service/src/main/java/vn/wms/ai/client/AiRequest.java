package vn.wms.ai.client;

import java.util.Objects;

/**
 * Một yêu cầu gửi mô hình AI.
 *
 * @param systemPrompt lời dặn cố định đặt đầu yêu cầu (được đánh dấu bộ nhớ đệm prompt)
 * @param userMessage  câu hỏi của người dùng
 * @param maxTokens    số token đầu ra tối đa
 */
public record AiRequest(String systemPrompt, String userMessage, long maxTokens) {

    public AiRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userMessage, "userMessage");
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens phải lớn hơn 0");
        }
    }
}
