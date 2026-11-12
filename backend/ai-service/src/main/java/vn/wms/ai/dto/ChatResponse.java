package vn.wms.ai.dto;

import java.util.List;

/**
 * Câu trả lời của trợ lý AI ({@code POST /api/ai/chat}).
 *
 * @param answer          nội dung trả lời bằng tiếng Việt (văn bản thường, có thể có dòng "- " và xuống dòng)
 * @param conversationId  mã hội thoại để gửi kèm câu hỏi tiếp theo
 * @param newConversation {@code true} khi câu hỏi này mở hội thoại mới (không gửi mã, mã đã hết hạn sau 1 giờ hoặc
 *                        không thuộc về người hỏi): giao diện có thể xóa các tin nhắn cũ đang hiển thị
 * @param truncated       câu trả lời bị cắt vì quá dài
 * @param refused         AI từ chối trả lời; {@code answer} là lời xin lỗi soạn sẵn, lượt này không bị tính
 * @param mode            {@code LIVE} (Claude) hoặc {@code MOCK} (trợ lý giả lập theo quy tắc)
 * @param lookups         các phép tra cứu dữ liệu đã chạy cho câu hỏi này, ví dụ {@code get_stock}
 * @param usage           số lượt AI đã dùng trong tháng sau câu hỏi này
 */
public record ChatResponse(String answer, String conversationId, boolean newConversation, boolean truncated,
                           boolean refused, String mode, List<String> lookups, AskResponse.Usage usage) {
}
