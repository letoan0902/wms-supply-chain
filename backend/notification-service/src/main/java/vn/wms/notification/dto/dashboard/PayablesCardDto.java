package vn.wms.notification.dto.dashboard;

import java.math.BigDecimal;

/**
 * Thẻ 2: Công nợ phải trả (Payables).
 */
public record PayablesCardDto(
        String title,
        BigDecimal amount,
        String currency,
        long dueSoonSuppliersCount,
        String subText,
        boolean visible
) {
    public static PayablesCardDto masked() {
        return new PayablesCardDto("Công nợ phải trả", BigDecimal.ZERO, "VNĐ", 0, "Không có quyền xem", false);
    }
}
