package vn.wms.ai.client;

/**
 * Kết quả của {@link AiClient#generateJson(AiJsonRequest)}. Bên gọi phải xét {@link #refused()} và
 * {@link #truncated()} trước khi đọc {@link #json()}: khi một trong hai cờ bật, JSON có thể không đúng lược đồ.
 *
 * @param json         văn bản JSON mô hình trả về (rỗng khi bị từ chối)
 * @param stopReason   lý do dừng, ví dụ {@code end_turn}, {@code max_tokens}, {@code refusal}
 * @param inputTokens  số token đầu vào được tính phí
 * @param outputTokens số token đầu ra được tính phí
 * @param truncated    chạm giới hạn token đầu ra hoặc cửa sổ ngữ cảnh, kết quả dở dang
 * @param refused      mô hình từ chối xử lý
 */
public record AiJsonReply(String json, String stopReason, long inputTokens, long outputTokens,
                          boolean truncated, boolean refused) {

    public static AiJsonReply completed(String json, String stopReason, long inputTokens, long outputTokens) {
        return new AiJsonReply(json, stopReason, inputTokens, outputTokens, false, false);
    }

    public static AiJsonReply truncated(String json, String stopReason, long inputTokens, long outputTokens) {
        return new AiJsonReply(json, stopReason, inputTokens, outputTokens, true, false);
    }

    public static AiJsonReply refusal(String stopReason, long inputTokens, long outputTokens) {
        return new AiJsonReply("", stopReason, inputTokens, outputTokens, false, true);
    }
}
