package vn.wms.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Câu hỏi gửi trợ lý AI ({@code POST /api/ai/chat}).
 *
 * @param message        nội dung câu hỏi, từ 1 đến 2000 ký tự
 * @param conversationId mã hội thoại nhận được ở câu trả lời trước; bỏ trống để bắt đầu hội thoại mới
 */
public record ChatRequest(
        @NotBlank(message = "Câu hỏi không được để trống")
        @Size(max = ChatRequest.MAX_MESSAGE_LENGTH, message = "Câu hỏi tối đa 2000 ký tự")
        String message,
        @Size(max = 64, message = "Mã hội thoại không hợp lệ")
        String conversationId) {

    public static final int MAX_MESSAGE_LENGTH = 2000;
}
