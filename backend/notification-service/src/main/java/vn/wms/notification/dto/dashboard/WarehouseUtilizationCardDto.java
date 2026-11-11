package vn.wms.notification.dto.dashboard;

/**
 * Thẻ 8: Tỷ lệ lấp đầy kho (Warehouse Utilization Rate).
 */
public record WarehouseUtilizationCardDto(
        String title,
        double occupancyRatePercentage,
        long usedLocations,
        long totalLocations,
        String subText,
        boolean visible
) {}
