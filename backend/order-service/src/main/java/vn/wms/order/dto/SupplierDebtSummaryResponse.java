package vn.wms.order.dto;

import java.math.BigDecimal;

/**
 * Báo cáo tổng quan công nợ nhà cung cấp (FR09 - WMS-08.1).
 */
public record SupplierDebtSummaryResponse(
        long totalSuppliers,
        long activeSuppliers,
        long overLimitSuppliers,
        BigDecimal totalDebt
) {
}
