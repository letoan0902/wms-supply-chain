package vn.wms.notification.dto.dashboard;

/**
 * Tổng hợp toàn diện 8 Thẻ KPI điều hành trên Executive Dashboard (WMS-16.1 - UC-10).
 */
public record KpiSummaryResponse(
        ReceivablesCardDto receivables,
        PayablesCardDto payables,
        InventoryValuationCardDto inventoryValuation,
        MonthlyRevenueCardDto monthlyRevenue,
        AvailableStockCardDto availableStock,
        PendingSalesOrdersCardDto pendingSalesOrders,
        PendingPurchaseOrdersCardDto pendingPurchaseOrders,
        WarehouseUtilizationCardDto warehouseUtilization,
        DashboardMetaDto meta
) {}
