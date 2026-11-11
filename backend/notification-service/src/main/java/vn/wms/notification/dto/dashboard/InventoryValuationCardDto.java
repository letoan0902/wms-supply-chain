package vn.wms.notification.dto.dashboard;

import java.math.BigDecimal;

/**
 * Thẻ 3: Giá trị tồn kho (Inventory Valuation).
 *
 * <p>Hiện tồn kho chưa có giá vốn nên {@code amount} luôn là {@code null} và {@code valuationMethod} là
 * {@value #METHOD_NOT_AVAILABLE}; giao diện hiển thị {@code subText} thay cho số tiền.
 */
public record InventoryValuationCardDto(
        String title,
        BigDecimal amount,
        String currency,
        String valuationMethod,
        String subText,
        boolean visible
) {
    /** Chưa có phương pháp tính vì tồn kho chưa có giá vốn. */
    public static final String METHOD_NOT_AVAILABLE = "NOT_AVAILABLE";
    public static final String NOT_AVAILABLE_SUB_TEXT = "Chưa tính được: tồn kho chưa có giá vốn";

    public static InventoryValuationCardDto masked() {
        return new InventoryValuationCardDto("Giá trị tồn kho", null, "VNĐ", METHOD_NOT_AVAILABLE, "Không có quyền xem", false);
    }
}
