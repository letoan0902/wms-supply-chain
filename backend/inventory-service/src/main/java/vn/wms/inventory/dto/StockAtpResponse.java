package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Thông tin tồn khả dụng ATP (Available To Promise) của sản phẩm (FR15 - WMS-11.1).
 * Công thức: ATP = OnHand - Reserved
 */
@Schema(description = "Thông tin tồn khả dụng ATP của sản phẩm")
public record StockAtpResponse(
        @Schema(description = "ID sản phẩm")
        Long productId,

        @Schema(description = "ID kho hàng (nếu lọc theo kho cụ thể)")
        Long warehouseId,

        @Schema(description = "Tổng tồn thực tế trong kho (OnHand)")
        BigDecimal onHandQuantity,

        @Schema(description = "Số lượng đã giữ chỗ cho các đơn hàng khác (Reserved)")
        BigDecimal reservedQuantity,

        @Schema(description = "Số lượng tồn khả dụng có thể cam kết bán (ATP = max(0, OnHand - Reserved))")
        BigDecimal atpQuantity
) {
    public static StockAtpResponse of(Long productId, Long warehouseId, BigDecimal onHand, BigDecimal reserved) {
        BigDecimal safeOnHand = onHand != null ? onHand : BigDecimal.ZERO;
        BigDecimal safeReserved = reserved != null ? reserved : BigDecimal.ZERO;
        BigDecimal atp = safeOnHand.subtract(safeReserved).max(BigDecimal.ZERO);
        return new StockAtpResponse(productId, warehouseId, safeOnHand, safeReserved, atp);
    }
}
