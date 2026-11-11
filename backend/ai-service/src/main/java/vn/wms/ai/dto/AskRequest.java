package vn.wms.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Câu hỏi gửi trợ lý AI ({@code POST /api/ai/ask}).
 *
 * @param question nội dung câu hỏi, từ 1 đến 2000 ký tự
 */
public record AskRequest(
        @NotBlank(message = "Câu hỏi không được để trống")
        @Size(max = AskRequest.MAX_QUESTION_LENGTH, message = "Câu hỏi tối đa 2000 ký tự")
        String question) {

    public static final int MAX_QUESTION_LENGTH = 2000;
}
