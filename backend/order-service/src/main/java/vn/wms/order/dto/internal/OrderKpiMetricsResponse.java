package vn.wms.order.dto.internal;

import java.math.BigDecimal;

/**
 * DTO chứa các chỉ số thống kê đơn hàng & tài chính phục vụ tổng hợp Dashboard KPI (WMS-16.1).
 */
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
) {}
