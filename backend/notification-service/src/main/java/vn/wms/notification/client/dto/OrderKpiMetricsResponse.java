package vn.wms.notification.client.dto;

import java.math.BigDecimal;

public record OrderKpiMetricsResponse(
        BigDecimal totalReceivables,
        long overdueCustomersCount,
        BigDecimal totalPayables,
        long dueSoonSuppliersCount,
        BigDecimal currentMonthRevenue,
        BigDecimal prevMonthRevenue,
        long pendingSalesOrdersCount,
        long urgentSalesOrdersCount,
        long pendingPurchaseOrdersCount,
        long arrivingTodayPurchaseOrdersCount
) {
    public static OrderKpiMetricsResponse empty() {
        return new OrderKpiMetricsResponse(
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0
        );
    }
}
