package vn.wms.notification.client.dto;

import java.math.BigDecimal;

public record InventoryKpiMetricsResponse(
        BigDecimal availableStockQuantity,
        BigDecimal totalStockQuantity,
        long occupiedLocations,
        long totalLocations,
        double occupancyRatePercentage
) {
    public static InventoryKpiMetricsResponse empty() {
        return new InventoryKpiMetricsResponse(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0.0);
    }
}
