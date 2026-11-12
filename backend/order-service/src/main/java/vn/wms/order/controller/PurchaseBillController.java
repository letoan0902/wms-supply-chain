package vn.wms.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import vn.wms.order.dto.PurchaseBillResponse;
import vn.wms.order.dto.PurchaseBillSummaryResponse;
import vn.wms.order.dto.RecordBillPaymentRequest;
import vn.wms.order.entity.PurchaseBillStatus;
import vn.wms.order.service.PurchaseBillService;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API Quản lý Hóa đơn mua hàng (Purchase Bill) & Công nợ phải trả Nhà cung cấp (FR11, FR13 - WMS-10.3).
 */
@RestController
@RequestMapping("/api/orders/purchase-bills")
@Tag(name = "Purchase Bills", description = "Quản lý Hóa đơn mua hàng liên kết GRN & Theo dõi công nợ NCC (Payment Payables - FR13)")
public class PurchaseBillController {

    private final PurchaseBillService purchaseBillService;

    public PurchaseBillController(PurchaseBillService purchaseBillService) {
        this.purchaseBillService = purchaseBillService;
    }

    @GetMapping
    @Operation(summary = "Danh sách hóa đơn mua hàng có phân trang, tìm kiếm và lọc trạng thái")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'ACCOUNTANT', 'WAREHOUSE_MANAGER', 'INTEGRATION')")
    public ApiResponse<PageResponse<PurchaseBillResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PurchaseBillStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long poId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(purchaseBillService.search(keyword, status, supplierId, poId, fromDate, toDate, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Báo cáo tổng quan công nợ mua hàng & hóa đơn NCC (Metrics Summary)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'ACCOUNTANT', 'WAREHOUSE_MANAGER', 'INTEGRATION')")
    public ApiResponse<PurchaseBillSummaryResponse> getSummary() {
        return ApiResponse.ok(purchaseBillService.getSummary());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết hóa đơn mua hàng theo ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'ACCOUNTANT', 'WAREHOUSE_MANAGER', 'INTEGRATION')")
    public ApiResponse<PurchaseBillResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(purchaseBillService.getById(id));
    }

    @GetMapping("/number/{billNumber}")
    @Operation(summary = "Chi tiết hóa đơn mua hàng theo Mã hóa đơn")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'ACCOUNTANT', 'WAREHOUSE_MANAGER', 'INTEGRATION')")
    public ApiResponse<PurchaseBillResponse> getByBillNumber(@PathVariable String billNumber) {
        return ApiResponse.ok(purchaseBillService.getByBillNumber(billNumber));
    }

    @GetMapping("/grn/{grnId}")
    @Operation(summary = "Tra cứu hóa đơn mua hàng theo ID phiếu nhập kho (GRN)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'ACCOUNTANT', 'WAREHOUSE_MANAGER', 'INTEGRATION')")
    public ApiResponse<PurchaseBillResponse> getByGrnId(@PathVariable Long grnId) {
        return ApiResponse.ok(purchaseBillService.getByGrnId(grnId));
    }

    @GetMapping("/po/{poId}")
    @Operation(summary = "Danh sách hóa đơn mua hàng theo ID đơn mua hàng (PO)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'ACCOUNTANT', 'WAREHOUSE_MANAGER', 'INTEGRATION')")
    public ApiResponse<List<PurchaseBillResponse>> listByPoId(@PathVariable Long poId) {
        return ApiResponse.ok(purchaseBillService.listByPoId(poId));
    }

    @PostMapping("/{id}/payments")
    @Operation(summary = "Ghi nhận thanh toán tiền hàng cho hóa đơn mua, chuyển trạng thái PAID khi trả đủ và trừ công nợ NCC (FR13)")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ApiResponse<PurchaseBillResponse> recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody RecordBillPaymentRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username) {
        String operator = (username != null && !username.isBlank()) ? username.trim() : "system";
        return ApiResponse.ok(purchaseBillService.recordPayment(id, request, operator));
    }
}
