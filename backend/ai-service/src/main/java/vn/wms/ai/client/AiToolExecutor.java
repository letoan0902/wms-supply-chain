package vn.wms.ai.client;

/**
 * Thực thi công cụ mô hình yêu cầu trong vòng hỏi đáp của {@link AiClient#chat(AiChatRequest, AiToolExecutor)}.
 * Cài đặt không được ném lỗi cho các tình huống nghiệp vụ (không tìm thấy, không có quyền, tên công cụ lạ): trả
 * {@link AiToolResult#error(String)} để mô hình tự giải thích cho người dùng.
 */
@FunctionalInterface
public interface AiToolExecutor {

    AiToolResult execute(AiToolCall call);
}
