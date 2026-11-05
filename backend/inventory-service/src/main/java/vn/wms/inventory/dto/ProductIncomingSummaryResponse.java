package vn.wms.inventory.dto;

import java.math.BigDecimal;

/**
 * Thống kê tổng số lượng hàng đang về (Incoming Stock) của một sản phẩm tại kho.
 */
public record ProductIncomingSummaryResponse(
        Long productId,
        Long warehouseId,
        BigDecimal incomingQuantity
) {}

