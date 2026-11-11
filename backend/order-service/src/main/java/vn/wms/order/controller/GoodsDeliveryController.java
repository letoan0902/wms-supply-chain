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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.order.dto.CreateGdnRequest;
import vn.wms.order.dto.GdnResponse;
import vn.wms.order.dto.GdnSummaryResponse;
import vn.wms.order.entity.GoodsDeliveryStatus;
import vn.wms.order.service.GoodsDeliveryService;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * REST API Phiếu xuất kho (Goods Delivery Note - GDN) (FR17 - WMS-12.1).
 */
@RestController
@RequestMapping("/api/orders/goods-delivery-notes")
@Tag(name = "Goods Delivery Notes", description = "Quản lý Phiếu xuất kho (GDN), luồng xuất kho SO -> GDN và điều phối giao hàng (FR17 - WMS-12.1)")
public class GoodsDeliveryController {

    private final GoodsDeliveryService goodsDeliveryService;

    public GoodsDeliveryController(GoodsDeliveryService goodsDeliveryService) {
        this.goodsDeliveryService = goodsDeliveryService;
    }

    @GetMapping
    @Operation(summary = "Danh sách phiếu xuất kho có phân trang, tìm kiếm và lọc đa tiêu chí")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<PageResponse<GdnResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) GoodsDeliveryStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long soId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDeliveryDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDeliveryDate,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(goodsDeliveryService.search(
                keyword, status, customerId, warehouseId, soId, fromDeliveryDate, toDeliveryDate, pageable
        ));
    }

    @GetMapping("/summary")
    @Operation(summary = "Báo cáo tổng quan tình hình xuất kho (GDN Metrics Summary)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<GdnSummaryResponse> getSummary() {
        return ApiResponse.ok(goodsDeliveryService.getSummary());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu xuất kho theo ID kèm chi tiết dòng hàng")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<GdnResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(goodsDeliveryService.getById(id));
    }

    @GetMapping("/number/{gdnNumber}")
    @Operation(summary = "Chi tiết phiếu xuất kho theo Mã GDN")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<GdnResponse> getByGdnNumber(@PathVariable String gdnNumber) {
        return ApiResponse.ok(goodsDeliveryService.getByGdnNumber(gdnNumber));
    }

    @GetMapping("/so/{soId}")
    @Operation(summary = "Danh sách các phiếu xuất kho đã lập theo một đơn bán SO")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<List<GdnResponse>> listBySoId(@PathVariable Long soId) {
        return ApiResponse.ok(goodsDeliveryService.listBySoId(soId));
    }

    @PostMapping
    @Operation(summary = "Lập mới Phiếu xuất kho từ Đơn bán hàng SO (Tự động sinh mã GDN, cập nhật số lượng đã xuất)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<GdnResponse>> create(
            @Valid @RequestBody CreateGdnRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        GdnResponse created = goodsDeliveryService.create(request, username);
        return ResponseEntity
                .created(URI.create("/api/orders/goods-delivery-notes/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Xác nhận xuất xưởng (Dispatch) hoàn tất xuất kho và gửi sự kiện order.dispatched")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse<GdnResponse> dispatch(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        return ApiResponse.ok(goodsDeliveryService.dispatch(id, username));
    }
}
