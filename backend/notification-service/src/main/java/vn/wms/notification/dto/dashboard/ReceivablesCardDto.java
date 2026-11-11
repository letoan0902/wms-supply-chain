package vn.wms.notification.dto.dashboard;

import java.math.BigDecimal;

/**
 * Thẻ 1: Công nợ phải thu (Receivables).
 */
public record ReceivablesCardDto(
        String title,
        BigDecimal amount,
        String currency,
        long overdueCustomersCount,
        String subText,
        boolean visible
) {
    public static ReceivablesCardDto masked() {
        return new ReceivablesCardDto("Công nợ phải thu", BigDecimal.ZERO, "VNĐ", 0, "Không có quyền xem", false);
    }
}
