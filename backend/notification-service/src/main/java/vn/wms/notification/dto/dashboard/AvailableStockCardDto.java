package vn.wms.notification.dto.dashboard;

import java.math.BigDecimal;

/**
 * Thẻ 5: Tồn kho khả dụng (Available To Promise - ATP).
 */
public record AvailableStockCardDto(
        String title,
        BigDecimal totalQuantity,
        String unit,
        String subText,
        boolean visible
) {}
