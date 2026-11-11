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
import vn.wms.order.dto.CreateCustomerRequest;
import vn.wms.order.dto.bulk.BulkCreateCustomerRequest;
import vn.wms.order.dto.bulk.BulkImportResponse;
import vn.wms.order.service.PartnerBulkImportService;
import vn.wms.order.dto.CustomerCreditCheckResponse;
import vn.wms.order.dto.CustomerDebtSummaryResponse;
import vn.wms.order.dto.CustomerResponse;
import vn.wms.order.dto.CustomerTransactionResponse;
import vn.wms.order.dto.RecordCustomerTransactionRequest;
import vn.wms.order.dto.UpdateCustomerRequest;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.service.CustomerService;

import java.math.BigDecimal;
import java.net.URI;

/**
 * REST API Quản lý Khách hàng / Đại lý & Hạn mức nợ (FR14 - WMS-08.2).
 */
@RestController
@RequestMapping("/api/orders/customers")
@Tag(name = "Customers", description = "Quản lý Khách hàng / Đại lý & Hạn mức nợ (Credit Limit - FR14)")
public class CustomerController {

    private final CustomerService customerService;
    private final PartnerBulkImportService bulkImportService;

    public CustomerController(CustomerService customerService, PartnerBulkImportService bulkImportService) {
        this.customerService = customerService;
        this.bulkImportService = bulkImportService;
    }

    @GetMapping
    @Operation(summary = "Danh sách khách hàng có phân trang, tìm kiếm và lọc theo loại / trạng thái / vượt hạn mức")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CustomerType type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean debtLocked,
            @RequestParam(required = false) Boolean overLimit,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return ApiResponse.ok(customerService.list(keyword, type, active, debtLocked, overLimit, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Báo cáo tổng quan công nợ & tình hình khách hàng")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    public ApiResponse<CustomerDebtSummaryResponse> getDebtSummary() {
        return ApiResponse.ok(customerService.getDebtSummary());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết hồ sơ khách hàng theo ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    public ApiResponse<CustomerResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(customerService.getById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Chi tiết hồ sơ khách hàng theo Mã")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    public ApiResponse<CustomerResponse> getByCode(@PathVariable String code) {
        return ApiResponse.ok(customerService.getByCode(code));
    }

    @GetMapping("/{id}/credit-check")
    @Operation(summary = "Kiểm tra hạn mức công nợ (Credit Check) trước khi tạo / duyệt đơn bán hàng (FR14)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    public ApiResponse<CustomerCreditCheckResponse> checkCredit(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal orderAmount) {
        return ApiResponse.ok(customerService.checkCredit(id, orderAmount));
    }

    @PostMapping
    @Operation(summary = "Tạo mới hồ sơ khách hàng / đại lý")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        return ResponseEntity
                .created(URI.create("/api/orders/customers/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    /**
     * Nhập hàng loạt khách hàng (WMS-22.4b). Cùng quyền với API tạo một khách hàng. Luôn trả 200 kèm kết quả
     * từng dòng; chỉ body rỗng, danh sách rỗng hoặc quá 500 dòng mới trả 400 VALIDATION_ERROR.
     */
    @PostMapping("/bulk")
    @Operation(summary = "Nhập hàng loạt khách hàng (tối đa 500 dòng, mã đã có thì bỏ qua)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<BulkImportResponse> bulkCreate(
            @Valid @RequestBody(required = false) BulkCreateCustomerRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Danh sách khách hàng cần nhập không được để trống");
        }
        return ApiResponse.ok(bulkImportService.importCustomers(request.items()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật hồ sơ khách hàng / đại lý theo ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<CustomerResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa mềm khách hàng (chuyển sang ngưng hoạt động)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Lịch sử biến động công nợ của khách hàng")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    public ApiResponse<PageResponse<CustomerTransactionResponse>> listTransactions(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(customerService.listTransactions(id, pageable));
    }

    @PostMapping("/{id}/transactions")
    @Operation(summary = "Ghi nhận phát sinh giao dịch công nợ với khách hàng (Bán hàng, Thanh toán, Trả hàng, Điều chỉnh)")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<CustomerTransactionResponse>> recordTransaction(
            @PathVariable Long id,
            @Valid @RequestBody RecordCustomerTransactionRequest request) {
        CustomerTransactionResponse created = customerService.recordTransaction(id, request);
        return ResponseEntity
                .created(URI.create("/api/orders/customers/" + id + "/transactions/" + created.id()))
                .body(ApiResponse.ok(created));
    }
}
