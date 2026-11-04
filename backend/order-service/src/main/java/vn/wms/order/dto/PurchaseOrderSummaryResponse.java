package vn.wms.order.dto;

import java.math.BigDecimal;

/**
 * Thống kê tổng quan đơn mua hàng (PO Metrics Summary).
 */
public record PurchaseOrderSummaryResponse(
        long totalOrders,
        long draftOrders,
        long pendingOrders,
        long approvedOrders,
        long rejectedOrders,
        long cancelledOrders,
        BigDecimal totalApprovedAmount
) {
}
