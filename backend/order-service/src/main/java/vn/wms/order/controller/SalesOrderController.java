package vn.wms.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.order.dto.AtpCheckRequest;
import vn.wms.order.dto.AtpCheckResponse;
import vn.wms.order.dto.CreateSalesOrderRequest;
import vn.wms.order.dto.SalesOrderResponse;
import vn.wms.order.dto.SalesOrderSummaryResponse;
import vn.wms.order.dto.UpdateSalesOrderRequest;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.service.SalesOrderService;

import java.net.URI;
import java.time.LocalDate;

/**
 * REST API Đơn bán hàng (Sales Order - SO) & Tồn khả dụng (ATP) (FR15 - WMS-11.1).
 */
@RestController
@RequestMapping("/api/orders/sales")
@Tag(name = "Sales Orders", description = "Quản lý Đơn bán hàng (SO) & Kiểm tra tồn khả dụng ATP (FR15 - WMS-11.1)")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @GetMapping
    @Operation(summary = "Danh sách đơn bán hàng (SO) có phân trang, tìm kiếm và lọc đa tiêu chí")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<PageResponse<SalesOrderResponse>> listSalesOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromOrderDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toOrderDate,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(salesOrderService.search(
                keyword, customerId, warehouseId, status, fromOrderDate, toOrderDate, pageable
        ));
    }

    @GetMapping("/summary")
    @Operation(summary = "Báo cáo tổng quan tình hình đơn bán hàng (SO Metrics Summary)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<SalesOrderSummaryResponse> getSummary() {
        return ApiResponse.ok(salesOrderService.getSummary());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn bán hàng theo ID (kèm danh sách mặt hàng và tồn khả dụng ATP)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<SalesOrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(salesOrderService.getById(id));
    }

    @GetMapping("/number/{soNumber}")
    @Operation(summary = "Chi tiết đơn bán hàng theo Mã SO")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<SalesOrderResponse> getBySoNumber(@PathVariable String soNumber) {
        return ApiResponse.ok(salesOrderService.getBySoNumber(soNumber));
    }

    @PostMapping("/check-atp")
    @Operation(summary = "Kiểm tra tồn khả dụng ATP (Available To Promise) trước khi tạo đơn")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE_MANAGER')")
    public ApiResponse<AtpCheckResponse> checkAtp(@Valid @RequestBody AtpCheckRequest request) {
        return ApiResponse.ok(salesOrderService.checkAtp(request));
    }

    @PostMapping
    @Operation(summary = "Lập mới đơn bán hàng (Tự động sinh mã SO, tính tiền, kiểm tra hạn mức nợ FR14 và tồn ATP FR15)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> create(
            @Valid @RequestBody CreateSalesOrderRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        SalesOrderResponse created = salesOrderService.create(request, username);
        return ResponseEntity
                .created(URI.create("/api/orders/sales/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật đơn bán hàng (chỉ khi đang ở trạng thái DRAFT hoặc BACKORDER)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    public ApiResponse<SalesOrderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSalesOrderRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.ok(salesOrderService.update(id, request, username));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đơn bán hàng (chỉ khi đang ở trạng thái DRAFT hoặc CANCELLED)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        salesOrderService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận đơn bán hàng (Re-check tồn ATP; nếu đủ chuyển sang CONFIRMED và gửi sự kiện order.created)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE_MANAGER')")
    public ApiResponse<SalesOrderResponse> confirm(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.ok(salesOrderService.confirm(id, username));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đơn bán hàng (chuyển sang trạng thái CANCELLED)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    public ApiResponse<SalesOrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(salesOrderService.cancel(id));
    }
}
