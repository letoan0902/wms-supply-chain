package vn.wms.order.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Báo cáo tổng hợp tỷ trọng các mặt hàng xuất bán chạy nhất (Trending Items - WMS-16.2).
 * Phục vụ khối biểu đồ Donut góc phải màn hình Executive Dashboard.
 */
public record TrendingItemsReportResponse(
        String title,
        BigDecimal totalVolume,
        String unit,
        String dateRange,
        String warehouseName,
        List<TrendingItemSliceDto> slices,
        ReportMeta meta
) {
    /**
     * Thông tin meta bổ trợ theo dõi và debug.
     */
    public record ReportMeta(
            Long companyId,
            Long warehouseId,
            Instant calculatedAt,
            boolean fromCache
    ) {}
}
