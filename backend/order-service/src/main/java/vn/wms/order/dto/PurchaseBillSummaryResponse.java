package vn.wms.order.dto;

import java.math.BigDecimal;

/**
 * Báo cáo tổng quan công nợ mua hàng & hóa đơn NCC (Purchase Bill Metrics - WMS-10.3).
 */
public record PurchaseBillSummaryResponse(
        long totalBills,
        long unpaidBills,
        long partiallyPaidBills,
        long paidBills,
        BigDecimal totalPayablesAmount,
        BigDecimal totalPaidAmount,
        BigDecimal totalRemainingAmount
) {
}
