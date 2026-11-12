package vn.wms.ai.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Yêu cầu mô hình trả lời đúng một đối tượng JSON theo lược đồ cho trước (structured outputs,
 * {@code output_config.format}). Dùng cho các tác vụ máy đọc kết quả, ví dụ trích xuất tài liệu (WMS-24.2).
 *
 * @param systemPrompt lời dặn cố định, đặt đầu yêu cầu và đánh dấu bộ nhớ đệm prompt; không chèn dữ liệu thay đổi
 *                     theo từng lần gọi vào đây
 * @param parts        nội dung tin nhắn của người dùng theo thứ tự (tài liệu, hình ảnh trước, câu dặn sau)
 * @param jsonSchema   lược đồ JSON của câu trả lời (mọi object phải có {@code additionalProperties: false})
 * @param maxTokens    số token đầu ra tối đa
 * @param timeout      thời gian chờ tối đa của lần gọi này
 */
public record AiJsonRequest(String systemPrompt, List<AiContentPart> parts, Map<String, Object> jsonSchema,
                            long maxTokens, Duration timeout) {

    public AiJsonRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(jsonSchema, "jsonSchema");
        Objects.requireNonNull(timeout, "timeout");
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("parts không được rỗng");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens phải lớn hơn 0");
        }
    }
}
