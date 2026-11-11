package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.inventory.dto.CreateStocktakeRequest;
import vn.wms.inventory.dto.StocktakeApproveRequest;
import vn.wms.inventory.dto.StocktakeCountLineResponse;
import vn.wms.inventory.dto.StocktakeCountRequest;
import vn.wms.inventory.dto.StocktakeFinishRequest;
import vn.wms.inventory.dto.StocktakeReasonRequest;
import vn.wms.inventory.dto.StocktakeRecountRequest;
import vn.wms.inventory.dto.StocktakeSessionResponse;
import vn.wms.inventory.dto.StocktakeVarianceLineResponse;
import vn.wms.inventory.dto.StocktakeVarianceReportResponse;
import vn.wms.inventory.entity.StocktakeStatus;
import vn.wms.inventory.service.StocktakeApprovalService;
import vn.wms.inventory.service.StocktakeService;

import java.time.LocalDate;

/**
 * REST API đợt kiểm kê: lập kế hoạch, bắt đầu (đóng băng vị trí, chụp tồn), đếm mù, hủy, tra cứu (WMS-14.1);
 * kết thúc đếm, chênh lệch, đếm lại, duyệt hai cấp và từ chối (WMS-14.2).
 */
@RestController
@RequestMapping("/api/inventory/stocktakes")
@Tag(name = "Stocktake", description = "Kiểm kê kho định kỳ: đóng băng vị trí, chụp tồn, đếm mù (WMS-14)")
public class StocktakeController {

    private final StocktakeService stocktakeService;
    private final StocktakeApprovalService stocktakeApprovalService;

    public StocktakeController(StocktakeService stocktakeService, StocktakeApprovalService stocktakeApprovalService) {
        this.stocktakeService = stocktakeService;
        this.stocktakeApprovalService = stocktakeApprovalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lập đợt kiểm kê ở trạng thái DRAFT (ADMIN, WAREHOUSE_MANAGER của kho)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<StocktakeSessionResponse> create(@Valid @RequestBody CreateStocktakeRequest request) {
        return ApiResponse.ok(stocktakeService.create(request));
    }

    @GetMapping
    @Operation(summary = "Danh sách đợt kiểm kê có phân trang, lọc theo kho, trạng thái, khoảng ngày dự kiến, mã đợt")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
    public ApiResponse<PageResponse<StocktakeSessionResponse>> list(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) StocktakeStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(stocktakeService.list(warehouseId, status, fromDate, toDate, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đợt kiểm kê kèm danh sách vị trí trong phạm vi")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
    public ApiResponse<StocktakeSessionResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(stocktakeService.getById(id));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Bắt đầu kiểm kê: đóng băng vị trí trong phạm vi và chụp tồn hệ thống (DRAFT -> COUNTING)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<StocktakeSessionResponse> start(@PathVariable Long id) {
        return ApiResponse.ok(stocktakeService.start(id));
    }

    @PostMapping("/{id}/counts")
    @Operation(summary = "Ghi số đếm mù cho một dòng (quét cộng dồn ADD hoặc nhập tay SET); phản hồi không có tồn hệ thống")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse<StocktakeCountLineResponse> count(@PathVariable Long id,
                                                         @Valid @RequestBody StocktakeCountRequest request) {
        return ApiResponse.ok(stocktakeService.count(id, request));
    }

    @GetMapping("/{id}/count-lines")
    @Operation(summary = "Danh sách dòng cần đếm cho màn hình đếm mù (không có tồn hệ thống), lọc đã đếm hoặc chưa đếm")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT')")
    public ApiResponse<PageResponse<StocktakeCountLineResponse>> listCountLines(
            @PathVariable Long id,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String binCode,
            @RequestParam(required = false) Boolean counted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(stocktakeService.listCountLines(id, productId, binCode, counted, page, size));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đợt kiểm kê trước khi duyệt: mở đóng băng, tồn kho không đổi")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<StocktakeSessionResponse> cancel(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) StocktakeReasonRequest request) {
        return ApiResponse.ok(stocktakeService.cancel(id, request != null ? request.reason() : null));
    }

    // ----------------------------- WMS-14.2: chênh lệch và duyệt -----------------------------

    @PostMapping("/{id}/finish")
    @Operation(summary = "Kết thúc đếm và tính chênh lệch (COUNTING -> COUNTED); còn dòng chưa đếm thì 409 trừ khi xác nhận tính bằng 0")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<StocktakeSessionResponse> finish(@PathVariable Long id,
                                                        @RequestBody(required = false) StocktakeFinishRequest request) {
        return ApiResponse.ok(stocktakeApprovalService.finish(id, request != null && request.confirmed()));
    }

    @GetMapping("/{id}/variances")
    @Operation(summary = "Báo cáo chênh lệch: tổng hợp và dòng chênh lệch có tồn hệ thống (ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER của kho)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<StocktakeVarianceReportResponse> variances(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean onlyDifferences,
            @RequestParam(required = false) String binCode,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(stocktakeApprovalService.variances(id, onlyDifferences, binCode, productId, page, size));
    }

    @PostMapping("/{id}/recount")
    @Operation(summary = "Yêu cầu đếm lại các dòng đã chọn trước khi duyệt (đợt quay lại COUNTING)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<StocktakeSessionResponse> recount(@PathVariable Long id,
                                                         @Valid @RequestBody StocktakeRecountRequest request) {
        return ApiResponse.ok(stocktakeApprovalService.recount(id, request.lineIds()));
    }

    @PutMapping("/{id}/lines/{lineId}/reason")
    @Operation(summary = "Ghi lý do chênh lệch cho một dòng (khi đợt chờ Quản lý kho duyệt)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<StocktakeVarianceLineResponse> setReason(@PathVariable Long id, @PathVariable Long lineId,
                                                                @Valid @RequestBody StocktakeReasonRequest request) {
        return ApiResponse.ok(stocktakeApprovalService.setReason(id, lineId, request.reason()));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Quản lý kho duyệt kết quả kiểm kê: không lệch thì hoàn tất, có lệch thì sinh phiếu điều chỉnh chờ Kế toán")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    public ApiResponse<StocktakeSessionResponse> approve(@PathVariable Long id,
                                                         @Valid @RequestBody(required = false) StocktakeApproveRequest request) {
        return ApiResponse.ok(stocktakeApprovalService.approveByManager(id, request != null ? request.notes() : null));
    }

    @PostMapping("/{id}/adjustment/approve")
    @Operation(summary = "Kế toán duyệt phiếu điều chỉnh: cập nhật tồn trong một transaction, mở đóng băng, hoàn tất đợt")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ApiResponse<StocktakeSessionResponse> approveAdjustment(@PathVariable Long id) {
        return ApiResponse.ok(stocktakeApprovalService.approveAdjustment(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối kết quả kiểm kê hoặc phiếu điều chỉnh (bắt buộc lý do): mở đóng băng, tồn kho không đổi")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<StocktakeSessionResponse> reject(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) StocktakeReasonRequest request) {
        return ApiResponse.ok(stocktakeApprovalService.reject(id, request != null ? request.reason() : null));
    }
}
