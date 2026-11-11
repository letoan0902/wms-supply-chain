package vn.wms.ai.client;

import java.util.List;

/**
 * Câu trả lời cuối của một câu hỏi có công cụ.
 *
 * @param text         nội dung trả lời (lời xin lỗi soạn sẵn khi bị từ chối)
 * @param stopReason   {@code stop_reason} của lần gọi cuối
 * @param inputTokens  tổng token đầu vào của mọi lần gọi
 * @param outputTokens tổng token đầu ra của mọi lần gọi
 * @param truncated    câu trả lời bị cắt vì chạm giới hạn token
 * @param refused      mô hình từ chối; không tính lượt
 * @param toolsUsed    tên các công cụ đã chạy theo thứ tự (để ghi log, kiểm thử)
 */
public record AiChatReply(String text, String stopReason, long inputTokens, long outputTokens, boolean truncated,
                          boolean refused, List<String> toolsUsed) {

    public AiChatReply {
        toolsUsed = List.copyOf(toolsUsed);
    }

    public static AiChatReply answered(String text, String stopReason, long inputTokens, long outputTokens,
                                       boolean truncated, List<String> toolsUsed) {
        return new AiChatReply(text, stopReason, inputTokens, outputTokens, truncated, false, toolsUsed);
    }

    public static AiChatReply refusal(String stopReason, long inputTokens, long outputTokens, List<String> toolsUsed) {
        return new AiChatReply(AiReply.REFUSAL_MESSAGE, stopReason, inputTokens, outputTokens, false, true, toolsUsed);
    }
}
