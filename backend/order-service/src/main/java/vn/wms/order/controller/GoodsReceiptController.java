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
import vn.wms.order.dto.CreateGrnRequest;
import vn.wms.order.dto.GrnResponse;
import vn.wms.order.dto.GrnSummaryResponse;
import vn.wms.order.dto.PoReceiptProgressResponse;
import vn.wms.order.entity.GoodsReceiptStatus;
import vn.wms.order.service.GoodsReceiptService;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * REST API Phiếu nhập kho (Goods Receipt Note - GRN) & Nhập từng phần (FR11 - WMS-10.1).
 */
@RestController
@RequestMapping("/api/orders/goods-receipt-notes")
@Tag(name = "Goods Receipt Notes", description = "Quản lý Phiếu nhập kho (PO → GRN, Partial Receipt) (FR11 - WMS-10.1)")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    public GoodsReceiptController(GoodsReceiptService goodsReceiptService) {
        this.goodsReceiptService = goodsReceiptService;
    }

    @PostMapping
    @Operation(summary = "Lập phiếu nhập kho GRN dựa trên đơn mua hàng PO đã duyệt (hỗ trợ nhập đủ hoặc nhiều đợt - Partial Receipt)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<GrnResponse>> create(
            @Valid @RequestBody CreateGrnRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String receivedBy) {
        GrnResponse created = goodsReceiptService.create(request, receivedBy);
        return ResponseEntity
                .created(URI.create("/api/orders/goods-receipt-notes/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận hoàn tất phiếu nhập kho GRN, chuyển PO sang Completed và sinh Hóa đơn mua hàng (FR11, FR13 - WMS-10.3)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse<GrnResponse> confirm(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", required = false) String confirmedBy) {
        return ApiResponse.ok(goodsReceiptService.confirm(id, confirmedBy));
    }

    @GetMapping
    @Operation(summary = "Danh sách phiếu nhập kho có phân trang, tìm kiếm và lọc đa tiêu chí")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<PageResponse<GrnResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) GoodsReceiptStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long poId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromReceiptDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toReceiptDate,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(goodsReceiptService.search(
                keyword, status, warehouseId, supplierId, poId, fromReceiptDate, toReceiptDate, pageable
        ));
    }

    @GetMapping("/summary")
    @Operation(summary = "Báo cáo tổng quan tình hình nhập kho (GRN Metrics Summary)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<GrnSummaryResponse> getSummary() {
        return ApiResponse.ok(goodsReceiptService.getSummary());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu nhập kho theo ID kèm danh sách dòng hàng đạt/hỏng")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<GrnResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(goodsReceiptService.getById(id));
    }

    @GetMapping("/number/{grnNumber}")
    @Operation(summary = "Chi tiết phiếu nhập kho theo Mã GRN")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<GrnResponse> getByGrnNumber(@PathVariable String grnNumber) {
        return ApiResponse.ok(goodsReceiptService.getByGrnNumber(grnNumber));
    }

    @GetMapping("/po/{poId}")
    @Operation(summary = "Danh sách các phiếu nhập kho đã lập theo một PO (lịch sử các đợt nhập hàng)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<List<GrnResponse>> listByPoId(@PathVariable Long poId) {
        return ApiResponse.ok(goodsReceiptService.listByPoId(poId));
    }

    @GetMapping("/po/{poId}/progress")
    @Operation(summary = "Theo dõi tiến độ nhập kho của PO (đã nhập bao nhiêu, còn lại bao nhiêu từng dòng mặt hàng)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<PoReceiptProgressResponse> getPoReceiptProgress(@PathVariable Long poId) {
        return ApiResponse.ok(goodsReceiptService.getPoReceiptProgress(poId));
    }
}
