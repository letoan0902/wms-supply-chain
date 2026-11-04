package vn.wms.order.client;

import java.math.BigDecimal;

/**
 * Thông tin tồn khả dụng ATP nhận từ inventory-service (FR15 - WMS-11.1).
 *
 * @param productId        id sản phẩm
 * @param warehouseId      id kho (nếu lọc theo kho cụ thể)
 * @param onHandQuantity   tổng tồn thực tế
 * @param reservedQuantity số lượng đã giữ chỗ cho đơn khác
 * @param atpQuantity      tồn khả dụng (ATP = max(0, OnHand - Reserved))
 */
public record AtpResponse(
        Long productId,
        Long warehouseId,
        BigDecimal onHandQuantity,
        BigDecimal reservedQuantity,
        BigDecimal atpQuantity
) {
    public static AtpResponse zero(Long productId, Long warehouseId) {
        return new AtpResponse(productId, warehouseId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
