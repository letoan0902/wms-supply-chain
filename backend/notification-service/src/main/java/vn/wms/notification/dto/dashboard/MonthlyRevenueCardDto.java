package vn.wms.notification.dto.dashboard;

import java.math.BigDecimal;

/**
 * Thẻ 4: Doanh số xuất bán tháng (Monthly Sales Revenue).
 */
public record MonthlyRevenueCardDto(
        String title,
        BigDecimal amount,
        String currency,
        Double growthRatePercentage,
        boolean isPositiveGrowth,
        String subText,
        boolean visible
) {
    public static MonthlyRevenueCardDto masked() {
        return new MonthlyRevenueCardDto("Doanh số xuất bán tháng", BigDecimal.ZERO, "VNĐ", 0.0, true, "Không có quyền xem", false);
    }
}
