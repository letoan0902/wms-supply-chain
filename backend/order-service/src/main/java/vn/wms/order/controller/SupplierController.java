package vn.wms.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.order.dto.CreateSupplierRequest;
import vn.wms.order.dto.bulk.BulkCreateSupplierRequest;
import vn.wms.order.dto.bulk.BulkImportResponse;
import vn.wms.order.service.PartnerBulkImportService;
import vn.wms.order.dto.RecordSupplierTransactionRequest;
import vn.wms.order.dto.SupplierDebtSummaryResponse;
import vn.wms.order.dto.SupplierResponse;
import vn.wms.order.dto.SupplierTransactionResponse;
import vn.wms.order.dto.UpdateSupplierRequest;
import vn.wms.order.service.SupplierService;

import java.net.URI;

/**
 * REST API Quản lý hồ sơ Nhà cung cấp & Công nợ (FR09 - WMS-08.1).
 */
@RestController
@RequestMapping("/api/orders/suppliers")
@Tag(name = "Suppliers", description = "Quản lý Nhà cung cấp, Hạn mức tín dụng & Lịch sử giao dịch công nợ (FR09 - WMS-08.1)")
public class SupplierController {

    private final SupplierService supplierService;
    private final PartnerBulkImportService bulkImportService;

    public SupplierController(SupplierService supplierService, PartnerBulkImportService bulkImportService) {
        this.supplierService = supplierService;
        this.bulkImportService = bulkImportService;
    }

    /**
     * Nhập hàng loạt nhà cung cấp (WMS-22.4b). Cùng quyền với API tạo một nhà cung cấp. Luôn trả 200 kèm kết quả
     * từng dòng; chỉ body rỗng, danh sách rỗng hoặc quá 500 dòng mới trả 400 VALIDATION_ERROR.
     */
    @PostMapping("/bulk")
    @Operation(summary = "Nhập hàng loạt nhà cung cấp (tối đa 500 dòng, mã đã có thì bỏ qua)")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT')")
    public ApiResponse<BulkImportResponse> bulkCreate(
            @Valid @RequestBody(required = false) BulkCreateSupplierRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Danh sách nhà cung cấp cần nhập không được để trống");
        }
        return ApiResponse.ok(bulkImportService.importSuppliers(request.items()));
    }

    @GetMapping
    @Operation(summary = "Danh sách nhà cung cấp có phân trang, tìm kiếm và lọc trạng thái")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<PageResponse<SupplierResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return ApiResponse.ok(supplierService.list(keyword, active, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Báo cáo tổng quan công nợ & tình hình nhà cung cấp")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<SupplierDebtSummaryResponse> getDebtSummary() {
        return ApiResponse.ok(supplierService.getDebtSummary());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết hồ sơ nhà cung cấp theo ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<SupplierResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(supplierService.getById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Chi tiết hồ sơ nhà cung cấp theo Mã")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<SupplierResponse> getByCode(@PathVariable String code) {
        return ApiResponse.ok(supplierService.getByCode(code));
    }

    @PostMapping
    @Operation(summary = "Tạo mới hồ sơ nhà cung cấp")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierResponse created = supplierService.create(request);
        return ResponseEntity
                .created(URI.create("/api/orders/suppliers/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật hồ sơ nhà cung cấp theo ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT')")
    public ApiResponse<SupplierResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ApiResponse.ok(supplierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa mềm nhà cung cấp (chuyển sang ngưng hoạt động)")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASING', 'ACCOUNTANT')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Lịch sử giao dịch công nợ của nhà cung cấp")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASING', 'ACCOUNTANT', 'INTEGRATION')")
    public ApiResponse<PageResponse<SupplierTransactionResponse>> listTransactions(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(supplierService.listTransactions(id, pageable));
    }

    @PostMapping("/{id}/transactions")
    @Operation(summary = "Ghi nhận phát sinh giao dịch công nợ với nhà cung cấp")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SupplierTransactionResponse>> recordTransaction(
            @PathVariable Long id,
            @Valid @RequestBody RecordSupplierTransactionRequest request) {
        SupplierTransactionResponse created = supplierService.recordTransaction(id, request);
        return ResponseEntity
                .created(URI.create("/api/orders/suppliers/" + id + "/transactions/" + created.id()))
                .body(ApiResponse.ok(created));
    }
}
