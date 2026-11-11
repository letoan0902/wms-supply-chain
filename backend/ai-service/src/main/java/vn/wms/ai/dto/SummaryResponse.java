package vn.wms.ai.dto;

import java.time.Instant;
import java.util.List;

/**
 * Bản tóm tắt số liệu điều hành bằng AI cho dashboard ({@code GET /api/ai/summary}, WMS-18.1).
 *
 * @param summary            toàn văn để hiển thị nhanh: các dòng "- " của {@code bullets}, rồi "Gợi ý:" và các dòng
 *                           của {@code actions}
 * @param bullets            3 đến 5 ý về các con số đáng chú ý
 * @param actions            1 đến 2 hành động gợi ý
 * @param generatedAt        thời điểm lập bản tóm tắt (bản lấy từ bộ nhớ đệm giữ nguyên thời điểm lập)
 * @param fromCache          {@code true} khi lấy từ bộ nhớ đệm 30 phút, không tốn lượt AI
 * @param mode               {@code LIVE} (Claude viết) hoặc {@code MOCK} (câu soạn sẵn từ số liệu)
 * @param warehouseId        kho đã yêu cầu, {@code null} là phạm vi mặc định của người xem
 * @param financialsIncluded bản tóm tắt có dùng chỉ số tài chính (chỉ ADMIN, ACCOUNTANT được xem)
 * @param usage              số lượt AI đã dùng trong tháng (không đổi khi lấy từ bộ nhớ đệm)
 */
public record SummaryResponse(String summary, List<String> bullets, List<String> actions, Instant generatedAt,
                              boolean fromCache, String mode, Long warehouseId, boolean financialsIncluded,
                              AskResponse.Usage usage) {

    public SummaryResponse {
        bullets = List.copyOf(bullets);
        actions = List.copyOf(actions);
    }

    public SummaryResponse cached(AskResponse.Usage currentUsage) {
        return new SummaryResponse(summary, bullets, actions, generatedAt, true, mode, warehouseId,
                financialsIncluded, currentUsage);
    }

    public SummaryResponse withUsage(AskResponse.Usage newUsage) {
        return new SummaryResponse(summary, bullets, actions, generatedAt, fromCache, mode, warehouseId,
                financialsIncluded, newUsage);
    }
}
