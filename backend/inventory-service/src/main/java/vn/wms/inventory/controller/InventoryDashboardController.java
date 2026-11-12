package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.inventory.dto.dashboard.RecentTransactionsResponse;
import vn.wms.inventory.dto.dashboard.StockByCategoryResponse;
import vn.wms.inventory.service.dashboard.InventoryDashboardService;

/**
 * Số liệu kho cho các biểu đồ dưới thẻ KPI của dashboard (WMS-16.4): tỷ trọng tồn kho theo nhóm hàng và bảng giao
 * dịch kho gần nhất. Cả hai nhận {@code warehouseId} và {@code dateRange} như các API khác của dashboard.
 */
@RestController
@RequestMapping("/api/inventory/dashboard")
@Tag(name = "Inventory Dashboard", description = "Tỷ trọng tồn kho theo nhóm hàng và giao dịch kho gần nhất cho dashboard")
public class InventoryDashboardController {

    private final InventoryDashboardService dashboardService;

    public InventoryDashboardController(InventoryDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stock-by-category")
    @Operation(summary = "Tỷ trọng tồn kho hiện tại theo nhóm hàng (theo số lượng khi tồn kho chưa có giá vốn)")
    // Sáu vai trò như thẻ tồn kho khả dụng trên dashboard; chỉ có số lượng, kho lọc theo phạm vi trong service.
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<StockByCategoryResponse> getStockByCategory(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String dateRange
    ) {
        return ApiResponse.ok(dashboardService.getStockByCategory(warehouseId, dateRange));
    }

    @GetMapping("/recent-transactions")
    @Operation(summary = "Các chứng từ kho gần nhất (nhập, xuất bán, điều chuyển, điều chỉnh) gom từ sổ kho")
    // Cùng vai trò với báo cáo kho (thẻ kho cho thấy cùng dữ liệu): người thực hiện và số chứng từ không mở cho
    // PURCHASING, SALES.
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
    public ApiResponse<RecentTransactionsResponse> getRecentTransactions(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(dashboardService.getRecentTransactions(warehouseId, dateRange, limit));
    }
}
