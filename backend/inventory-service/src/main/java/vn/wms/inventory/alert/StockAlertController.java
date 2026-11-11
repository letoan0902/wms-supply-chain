package vn.wms.inventory.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;

/**
 * API cảnh báo tồn kho (WMS-15.1): hàng cận hạn, hết hạn, tồn lâu và tồn thấp, tính trực tiếp từ tồn hiện tại cho bảng
 * tồn kho và chuông thông báo; nút quét tay cho ADMIN. inventory-service không bật method security nên quyền được
 * kiểm tra tường minh trong code.
 */
@RestController
@RequestMapping("/api/inventory/alerts")
@Tag(name = "Stock Alerts", description = "Cảnh báo hàng cận hạn, hết hạn, tồn lâu và tồn thấp")
public class StockAlertController {

    private final StockAlertService alertService;
    private final StockAlertScanService scanService;

    public StockAlertController(StockAlertService alertService, StockAlertScanService scanService) {
        this.alertService = alertService;
        this.scanService = scanService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT', 'INTEGRATION')")
    @Operation(summary = "Danh sách cảnh báo tồn kho hiện tại trong phạm vi kho của người dùng, có lọc và phân trang")
    public ApiResponse<PageResponse<StockAlertResponse>> search(
            @RequestParam(required = false) StockAlertKind kind,
            @RequestParam(required = false) StockAlertSeverity severity,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(alertService.search(kind, severity, warehouseId, productId, keyword, pageable));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'SALES', 'ACCOUNTANT', 'INTEGRATION')")
    @Operation(summary = "Số cảnh báo theo loại và mức độ, kèm 5 cảnh báo nghiêm trọng nhất")
    public ApiResponse<StockAlertSummaryResponse> summary(@RequestParam(required = false) Long warehouseId) {
        return ApiResponse.ok(alertService.summary(warehouseId));
    }

    @PostMapping("/scan")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chạy ngay tác vụ quét cảnh báo cho doanh nghiệp của mình (chỉ ADMIN); mỗi loại chỉ gửi một lần mỗi ngày")
    public ApiResponse<StockAlertScanResponse> scan() {
        CurrentUser user = UserContext.requireCurrentUser();
        if (!user.hasRole(Roles.ADMIN)) {
            throw ApiException.forbidden("Chỉ Quản trị viên (ADMIN) mới được chạy quét cảnh báo tồn kho");
        }
        return ApiResponse.ok(scanService.scanCurrentCompany(user.username()));
    }
}
