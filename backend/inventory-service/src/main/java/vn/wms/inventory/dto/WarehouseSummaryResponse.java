package vn.wms.inventory.dto;

import java.math.BigDecimal;

/**
 * Thống kê tổng hợp năng lực đa kho (WMS-05.2 - Warehouse Summary Cards).
 */
public record WarehouseSummaryResponse(
        long totalWarehouses,
        long activeWarehouses,
        long totalCapacity,
        long totalLocations,
        long occupiedLocations,
        long availableLocations,
        double overallUtilizationRate,
        long totalProducts,
        BigDecimal totalStockQuantity
) {}

