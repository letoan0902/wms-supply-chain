package vn.wms.ai.client;

/**
 * Kết quả một lần gọi mô hình AI.
 *
 * @param text         nội dung trả lời; khi bị từ chối là lời xin lỗi soạn sẵn {@link #REFUSAL_MESSAGE}
 * @param stopReason   lý do dừng do mô hình trả về, ví dụ {@code end_turn}, {@code max_tokens}, {@code refusal}
 * @param inputTokens  số token đầu vào được tính phí (0 ở chế độ giả lập)
 * @param outputTokens số token đầu ra được tính phí (0 ở chế độ giả lập)
 * @param truncated    câu trả lời bị cắt vì chạm giới hạn token đầu ra
 * @param refused      mô hình từ chối trả lời; lượt này không bị tính vào hạn mức
 */
public record AiReply(String text, String stopReason, long inputTokens, long outputTokens,
                      boolean truncated, boolean refused) {

    /** Câu trả lời cho người dùng khi mô hình từ chối, không lộ chi tiết phân loại của nhà cung cấp. */
    public static final String REFUSAL_MESSAGE = "Xin lỗi, trợ lý không thể trả lời câu hỏi này. "
            + "Bạn hãy diễn đạt lại câu hỏi hoặc hỏi về việc quản lý kho, hàng hóa, đơn hàng trong hệ thống.";

    /** Câu trả lời bình thường (có thể bị cắt nếu {@code truncated}). */
    public static AiReply answered(String text, String stopReason, long inputTokens, long outputTokens,
                                   boolean truncated) {
        return new AiReply(text, stopReason, inputTokens, outputTokens, truncated, false);
    }

    /** Mô hình từ chối trả lời: trả lời người dùng bằng lời xin lỗi soạn sẵn và không trừ lượt. */
    public static AiReply refusal(String stopReason, long inputTokens, long outputTokens) {
        return new AiReply(REFUSAL_MESSAGE, stopReason, inputTokens, outputTokens, false, true);
    }
}
