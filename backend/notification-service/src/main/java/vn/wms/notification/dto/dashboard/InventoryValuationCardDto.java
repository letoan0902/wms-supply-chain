package vn.wms.notification.dto.dashboard;

import java.math.BigDecimal;

/**
 * Thẻ 3: Giá trị tồn kho (Inventory Valuation).
 */
public record InventoryValuationCardDto(
        String title,
        BigDecimal amount,
        String currency,
        String valuationMethod,
        String subText,
        boolean visible
) {
    public static InventoryValuationCardDto masked() {
        return new InventoryValuationCardDto("Giá trị tồn kho", BigDecimal.ZERO, "VNĐ", "WEIGHTED_AVERAGE", "Không có quyền xem", false);
    }
}
