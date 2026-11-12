package vn.wms.ai.dto;

/**
 * Mức dùng AI trong tháng của doanh nghiệp đang đăng nhập ({@code GET /api/ai/usage}).
 *
 * @param used  số lượt đã dùng trong tháng (giờ Việt Nam)
 * @param max   giới hạn của gói, {@code -1} là không giới hạn, {@code null} khi chưa có bộ nhớ đệm thuê bao
 * @param month tháng đang đếm, dạng {@code yyyy-MM}
 * @param plan  mã gói ({@code FREE}, {@code TRIAL}, {@code PRO}), {@code null} khi chưa có bộ nhớ đệm thuê bao
 */
public record AiUsageResponse(long used, Long max, String month, String plan) {
}
