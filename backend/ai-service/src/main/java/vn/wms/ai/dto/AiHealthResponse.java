package vn.wms.ai.dto;

/**
 * Trạng thái của ai-service ({@code GET /api/ai/health}). Không chứa khóa hay thông tin bí mật.
 *
 * @param status luôn {@code UP} khi service trả lời được
 * @param mode   {@code MOCK} (câu trả lời mẫu) hoặc {@code LIVE} (gọi Claude thật)
 * @param model  mã mô hình Claude đang cấu hình
 */
public record AiHealthResponse(String status, String mode, String model) {
}
