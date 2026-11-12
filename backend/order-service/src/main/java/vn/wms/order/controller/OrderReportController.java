package vn.wms.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.order.dto.report.TrendingItemsReportResponse;
import vn.wms.order.service.OrderReportService;

/**
 * Controller cung cấp các báo cáo thống kê chuyên sâu về đơn hàng và bán hàng (WMS-16.2).
 * Phục vụ trực tiếp cho Biểu đồ Donut (Trending Items) trên màn hình Executive Dashboard.
 */
@RestController
@RequestMapping("/api/orders/reports")
@RequiredArgsConstructor
@Tag(name = "Order Reports", description = "Báo cáo thống kê đơn hàng, tỷ trọng mặt hàng xuất bán (Trending Items)")
public class OrderReportController {

    private final OrderReportService orderReportService;

    /**
     * Lấy dữ liệu báo cáo tỷ trọng các mặt hàng xuất bán chạy nhất.
     *
     * @param warehouseId ID kho (tùy chọn); bỏ trống thì lấy mọi kho trong phạm vi của người xem
     * @param dateRange   Khoảng thời gian: TODAY, 7_DAYS, THIS_MONTH, THIS_QUARTER, ALL_TIME; giá trị khác trả 400
     * @param limit       Số lượng mặt hàng hiển thị độc lập, từ 1 đến 20 (mặc định 5, phần còn lại gom vào 'Khác')
     * @return Báo cáo tỷ trọng kèm danh sách lát cắt (slices) đã gán mã màu Recharts
     */
    @GetMapping("/trending-items")
    @Operation(summary = "Thống kê Top mặt hàng xuất bán chạy nhất (Trending Items Donut Chart)")
    // Dashboard mở cho cả sáu vai trò như thẻ KPI (docs/SECURITY.md); báo cáo chỉ có số lượng, không có số tiền,
    // và được lọc theo phạm vi kho của người xem trong service.
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<TrendingItemsReportResponse> getTrendingItems(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(orderReportService.getTrendingItems(warehouseId, dateRange, limit));
    }
}
