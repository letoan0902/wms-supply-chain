package vn.wms.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.dto.StockTransferResponse;
import vn.wms.inventory.dto.UpdateStockTransferRequest;
import vn.wms.inventory.entity.StockTransferStatus;
import vn.wms.inventory.service.StockTransferService;

import java.util.Map;

/**
 * REST API Quản lý điều chuyển hàng hóa nội bộ giữa các kho (Stock Transfer - FR08, WMS-13.1).
 */
@RestController
@RequestMapping("/api/inventory/transfers")
@Tag(name = "Stock Transfer Management", description = "Quản lý luồng điều chuyển hàng hóa nội bộ giữa các kho (WMS-13)")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo mới một lệnh điều chuyển hàng hóa ở trạng thái DRAFT (WMS-13.1)")
    public ApiResponse<StockTransferResponse> createTransfer(@Valid @RequestBody CreateStockTransferRequest request) {
        return ApiResponse.ok(stockTransferService.createTransfer(request));
    }

    @GetMapping
    @Operation(summary = "Tra cứu danh sách lệnh điều chuyển có phân trang và bộ lọc (WMS-13.1)")
    public ApiResponse<Page<StockTransferResponse>> listTransfers(
            @RequestParam(required = false) StockTransferStatus status,
            @RequestParam(required = false) Long sourceWarehouseId,
            @RequestParam(required = false) Long destinationWarehouseId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(stockTransferService.listTransfers(status, sourceWarehouseId, destinationWarehouseId, keyword, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin chi tiết một lệnh điều chuyển hàng hóa")
    public ApiResponse<StockTransferResponse> getTransferById(@PathVariable Long id) {
        return ApiResponse.ok(stockTransferService.getTransferById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật lệnh điều chuyển hàng hóa khi còn ở trạng thái DRAFT")
    public ApiResponse<StockTransferResponse> updateTransfer(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockTransferRequest request
    ) {
        return ApiResponse.ok(stockTransferService.updateTransfer(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hủy một lệnh điều chuyển hàng hóa khi còn ở trạng thái DRAFT")
    public ApiResponse<StockTransferResponse> cancelTransfer(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        return ApiResponse.ok(stockTransferService.cancelTransfer(id, reason));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy lệnh điều chuyển qua phương thức POST (kèm lý do)")
    public ApiResponse<StockTransferResponse> cancelTransferPost(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : null;
        return ApiResponse.ok(stockTransferService.cancelTransfer(id, reason));
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Phê duyệt xuất kho và chuyển hàng đi đường (DRAFT -> IN_TRANSIT) (WMS-13.1)")
    public ApiResponse<StockTransferResponse> dispatchTransfer(@PathVariable Long id) {
        return ApiResponse.ok(stockTransferService.dispatchTransfer(id));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Xác nhận nhận hàng tại kho đích và xử lý biên bản tổn thất (IN_TRANSIT -> COMPLETED / PARTIAL_RECEIVED) (WMS-13.2)")
    public ApiResponse<StockTransferResponse> receiveTransfer(
            @PathVariable Long id,
            @Valid @RequestBody ReceiveStockTransferRequest request
    ) {
        return ApiResponse.ok(stockTransferService.receiveTransfer(id, request));
    }
}

