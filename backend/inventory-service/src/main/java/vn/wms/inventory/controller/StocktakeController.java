package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.inventory.dto.CreateStocktakeRequest;
import vn.wms.inventory.dto.StocktakeCountLineResponse;
import vn.wms.inventory.dto.StocktakeCountRequest;
import vn.wms.inventory.dto.StocktakeReasonRequest;
import vn.wms.inventory.dto.StocktakeSessionResponse;
import vn.wms.inventory.entity.StocktakeStatus;
import vn.wms.inventory.service.StocktakeService;

import java.time.LocalDate;

/**
 * REST API đợt kiểm kê (WMS-14.1): lập kế hoạch, bắt đầu (đóng băng vị trí, chụp tồn), đếm mù, hủy, tra cứu.
 */
@RestController
@RequestMapping("/api/inventory/stocktakes")
@Tag(name = "Stocktake", description = "Kiểm kê kho định kỳ: đóng băng vị trí, chụp tồn, đếm mù (WMS-14)")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lập đợt kiểm kê ở trạng thái DRAFT (ADMIN, WAREHOUSE_MANAGER của kho)")
    public ApiResponse<StocktakeSessionResponse> create(@Valid @RequestBody CreateStocktakeRequest request) {
        return ApiResponse.ok(stocktakeService.create(request));
    }

    @GetMapping
    @Operation(summary = "Danh sách đợt kiểm kê có phân trang, lọc theo kho, trạng thái, khoảng ngày dự kiến, mã đợt")
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
    public ApiResponse<StocktakeSessionResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(stocktakeService.getById(id));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Bắt đầu kiểm kê: đóng băng vị trí trong phạm vi và chụp tồn hệ thống (DRAFT -> COUNTING)")
    public ApiResponse<StocktakeSessionResponse> start(@PathVariable Long id) {
        return ApiResponse.ok(stocktakeService.start(id));
    }

    @PostMapping("/{id}/counts")
    @Operation(summary = "Ghi số đếm mù cho một dòng (quét cộng dồn ADD hoặc nhập tay SET); phản hồi không có tồn hệ thống")
    public ApiResponse<StocktakeCountLineResponse> count(@PathVariable Long id,
                                                         @Valid @RequestBody StocktakeCountRequest request) {
        return ApiResponse.ok(stocktakeService.count(id, request));
    }

    @GetMapping("/{id}/count-lines")
    @Operation(summary = "Danh sách dòng cần đếm cho màn hình đếm mù (không có tồn hệ thống), lọc đã đếm hoặc chưa đếm")
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
    public ApiResponse<StocktakeSessionResponse> cancel(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) StocktakeReasonRequest request) {
        return ApiResponse.ok(stocktakeService.cancel(id, request != null ? request.reason() : null));
    }
}
