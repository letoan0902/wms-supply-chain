package vn.wms.ai.client;

import java.util.Objects;

/**
 * Một tin nhắn trong lịch sử hội thoại (chỉ phần văn bản: câu hỏi của người dùng hoặc câu trả lời cuối của trợ lý).
 *
 * @param role {@code user} hoặc {@code assistant}
 * @param text nội dung
 */
public record AiChatMessage(String role, String text) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public AiChatMessage {
        Objects.requireNonNull(text, "text");
        if (!USER.equals(role) && !ASSISTANT.equals(role)) {
            throw new IllegalArgumentException("role phải là user hoặc assistant");
        }
    }

    public static AiChatMessage user(String text) {
        return new AiChatMessage(USER, text);
    }

    public static AiChatMessage assistant(String text) {
        return new AiChatMessage(ASSISTANT, text);
    }
}
