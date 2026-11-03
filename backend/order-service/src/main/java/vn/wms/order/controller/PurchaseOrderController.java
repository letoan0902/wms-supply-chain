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
import vn.wms.order.dto.CreatePurchaseOrderRequest;
import vn.wms.order.dto.PurchaseOrderResponse;
import vn.wms.order.dto.PurchaseOrderSummaryResponse;
import vn.wms.order.dto.RejectPurchaseOrderRequest;
import vn.wms.order.dto.UpdatePurchaseOrderRequest;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.service.PurchaseOrderService;

import java.net.URI;
import java.time.LocalDate;

/**
 * REST API Đơn mua hàng (Purchase Order - PO) & Phê duyệt (FR10 - WMS-09.1).
 */
@RestController
@RequestMapping("/api/orders/purchase")
@Tag(name = "Purchase Orders", description = "Quản lý Đơn mua hàng (PO) & Phê duyệt đơn mua (FR10 - WMS-09.1)")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    @Operation(summary = "Danh sách đơn mua hàng (PO) có phân trang, tìm kiếm và lọc đa tiêu chí")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<PageResponse<PurchaseOrderResponse>> listPurchaseOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromOrderDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toOrderDate,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        if (keyword == null && supplierId == null && warehouseId == null && status == null && fromOrderDate == null && toOrderDate == null) {
            return ApiResponse.ok(purchaseOrderService.list(pageable));
        }

        return ApiResponse.ok(purchaseOrderService.search(
                keyword, supplierId, warehouseId, status, fromOrderDate, toOrderDate, pageable
        ));
    }

    @GetMapping("/summary")
    @Operation(summary = "Báo cáo tổng quan tình hình đơn mua hàng (PO Metrics Summary)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<PurchaseOrderSummaryResponse> getSummary() {
        return ApiResponse.ok(purchaseOrderService.getSummary());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn mua hàng theo ID (kèm danh sách mặt hàng)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<PurchaseOrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(purchaseOrderService.getById(id));
    }

    @GetMapping("/number/{poNumber}")
    @Operation(summary = "Chi tiết đơn mua hàng theo Mã PO")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'WAREHOUSE_MANAGER', 'ACCOUNTANT')")
    public ApiResponse<PurchaseOrderResponse> getByPoNumber(@PathVariable String poNumber) {
        return ApiResponse.ok(purchaseOrderService.getByPoNumber(poNumber));
    }

    @PostMapping
    @Operation(summary = "Lập mới đơn mua hàng (Tự động sinh mã PO, tính thành tiền dòng, VAT, chiết khấu, Grand Total)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        PurchaseOrderResponse created = purchaseOrderService.create(request);
        return ResponseEntity
                .created(URI.create("/api/orders/purchase/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật đơn mua hàng (chỉ khi đang ở trạng thái DRAFT hoặc PENDING)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING')")
    public ApiResponse<PurchaseOrderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePurchaseOrderRequest request) {
        return ApiResponse.ok(purchaseOrderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đơn mua hàng (chỉ khi đang ở trạng thái DRAFT, PENDING hoặc CANCELLED)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Phê duyệt đơn mua hàng (PENDING -> APPROVED, phát sự kiện 'order.created' sang RabbitMQ)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING')")
    public ApiResponse<PurchaseOrderResponse> approve(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.ok(purchaseOrderService.approve(id, username));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối phê duyệt đơn mua hàng (PENDING -> REJECTED kèm lý do)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING')")
    public ApiResponse<PurchaseOrderResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectPurchaseOrderRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.ok(purchaseOrderService.reject(id, request, username));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đơn mua hàng (chuyển sang trạng thái CANCELLED)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING')")
    public ApiResponse<PurchaseOrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(purchaseOrderService.cancel(id));
    }
}
