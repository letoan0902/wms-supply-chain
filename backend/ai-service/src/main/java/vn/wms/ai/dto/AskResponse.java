package vn.wms.ai.dto;

/**
 * Câu trả lời của trợ lý AI.
 *
 * @param answer    nội dung trả lời (tiếng Việt)
 * @param truncated câu trả lời bị cắt vì chạm giới hạn độ dài
 * @param refused   mô hình từ chối trả lời; {@code answer} là lời xin lỗi soạn sẵn và lượt này không bị tính
 * @param usage     số lượt AI đã dùng trong tháng sau câu hỏi này
 */
public record AskResponse(String answer, boolean truncated, boolean refused, Usage usage) {

    /**
     * @param used số lượt đã dùng trong tháng
     * @param max  giới hạn của gói, {@code -1} là không giới hạn, {@code null} khi chưa đọc được thuê bao
     */
    public record Usage(long used, Long max) {
    }
}
