package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.inventory.dto.StockAdjustmentResponse;
import vn.wms.inventory.entity.StockAdjustmentStatus;
import vn.wms.inventory.service.StocktakeApprovalService;

import java.time.LocalDate;

/**
 * REST API tra cứu phiếu điều chỉnh tồn kho sinh từ kiểm kê (WMS-14.2). Duyệt và từ chối đi qua
 * {@code /api/inventory/stocktakes/{id}/adjustment/approve} và {@code /api/inventory/stocktakes/{id}/reject}.
 */
@RestController
@RequestMapping("/api/inventory/stock-adjustments")
@Tag(name = "Stock Adjustment", description = "Phiếu điều chỉnh tồn kho sinh từ chênh lệch kiểm kê (WMS-14.2)")
public class StockAdjustmentController {

    private final StocktakeApprovalService stocktakeApprovalService;

    public StockAdjustmentController(StocktakeApprovalService stocktakeApprovalService) {
        this.stocktakeApprovalService = stocktakeApprovalService;
    }

    @GetMapping
    @Operation(summary = "Danh sách phiếu điều chỉnh, lọc theo kho, trạng thái, khoảng ngày tạo, mã phiếu (ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER)")
    public ApiResponse<PageResponse<StockAdjustmentResponse>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) StockAdjustmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(stocktakeApprovalService.listAdjustments(warehouseId, status, fromDate, toDate, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu điều chỉnh kèm các dòng, tồn trước và sau khi duyệt")
    public ApiResponse<StockAdjustmentResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(stocktakeApprovalService.getAdjustment(id));
    }
}
