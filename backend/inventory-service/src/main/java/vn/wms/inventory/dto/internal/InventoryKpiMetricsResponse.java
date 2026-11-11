package vn.wms.inventory.dto.internal;

import java.math.BigDecimal;

/**
 * DTO chứa các chỉ số thống kê tồn kho & mặt bằng ô/kệ phục vụ Dashboard KPI (WMS-16.1).
 */
public record InventoryKpiMetricsResponse(
        BigDecimal availableStockQuantity,
        BigDecimal totalStockQuantity,
        long occupiedLocations,
        long totalLocations,
        double occupancyRatePercentage
) {}
