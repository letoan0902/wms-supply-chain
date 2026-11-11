package vn.wms.notification.dto.dashboard;

import java.time.Instant;

/**
 * Metadata bổ trợ cho bảng điều khiển KPI.
 */
public record DashboardMetaDto(
        Long companyId,
        Long warehouseId,
        String warehouseName,
        String dateRange,
        Instant calculatedAt,
        boolean fromCache
) {}
