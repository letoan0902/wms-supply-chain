package vn.wms.order.dto;

import java.math.BigDecimal;

/**
 * Báo cáo tổng quan công nợ khách hàng (FR14 - WMS-08.2).
 */
public record CustomerDebtSummaryResponse(
        long totalCustomers,
        long activeCustomers,
        long debtLockedCustomers,
        long overLimitCustomers,
        BigDecimal totalDebt
) {
}
