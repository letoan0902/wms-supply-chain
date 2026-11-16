package vn.wms.auth.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.payment.entity.PaymentStatus;
import vn.wms.auth.platform.dto.ApproveCompanyRequest;
import vn.wms.auth.platform.dto.PlatformCompanyDetailResponse;
import vn.wms.auth.platform.dto.PlatformCompanySummary;
import vn.wms.auth.platform.dto.PlatformOverviewResponse;
import vn.wms.auth.platform.dto.PlatformPayment;
import vn.wms.auth.platform.dto.RejectCompanyRequest;
import vn.wms.auth.platform.dto.UpdateCompanyStatusRequest;
import vn.wms.auth.platform.service.PlatformService;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;

import java.time.LocalDate;

/**
 * Quản trị nền tảng (docs/SAAS_SPEC.md mục 6): chỉ {@code SUPER_ADMIN}, chặn ở cả {@code SecurityConfig} và từng
 * phương thức. Vai trò khác nhận 403 {@code FORBIDDEN}, chưa đăng nhập nhận 401. Doanh nghiệp hệ thống
 * {@code PLATFORM} không bao giờ xuất hiện trong kết quả; xem, đổi trạng thái, duyệt hoặc từ chối nó trả 404.
 */
@RestController
@RequestMapping("/api/auth/platform")
@Tag(name = "Platform", description = "Quản trị nền tảng: thống kê và quản lý doanh nghiệp khách hàng (chỉ SUPER_ADMIN)")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Số liệu tổng quan: doanh nghiệp, thuê bao, người dùng, doanh thu, 6 tháng gần nhất")
    public ApiResponse<PlatformOverviewResponse> overview() {
        return ApiResponse.ok(platformService.overview());
    }

    @GetMapping("/companies")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Danh sách doanh nghiệp khách hàng, lọc theo từ khóa, trạng thái, gói; mới tạo trước")
    public ApiResponse<PageResponse<PlatformCompanySummary>> companies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CompanyStatus status,
            @RequestParam(required = false) String plan,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(platformService.companies(keyword, status, plan, page, size));
    }

    @GetMapping("/companies/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Chi tiết doanh nghiệp: giới hạn gói, mức dùng, tài khoản ADMIN, 10 thanh toán gần nhất")
    public ApiResponse<PlatformCompanyDetailResponse> company(@PathVariable Long id) {
        return ApiResponse.ok(platformService.companyDetail(id));
    }

    @PutMapping("/companies/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Tạm khóa (ACTIVE thành SUSPENDED) hoặc mở khóa (SUSPENDED thành ACTIVE) doanh nghiệp")
    public ApiResponse<PlatformCompanySummary> updateStatus(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateCompanyStatusRequest request,
                                                            Authentication authentication) {
        return ApiResponse.ok(platformService.updateStatus(id, request, authentication.getName()));
    }

    @PostMapping("/companies/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Duyệt bản đăng ký đang chờ (PENDING): doanh nghiệp hoạt động ngay với gói TRIAL hoặc PRO")
    public ApiResponse<PlatformCompanySummary> approve(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) ApproveCompanyRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(platformService.approve(id, request, authentication.getName()));
    }

    @PostMapping("/companies/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Từ chối bản đăng ký đang chờ (PENDING): doanh nghiệp chuyển SUSPENDED, không đăng nhập được")
    public ApiResponse<PlatformCompanySummary> reject(@PathVariable Long id,
                                                      @Valid @RequestBody RejectCompanyRequest request,
                                                      Authentication authentication) {
        return ApiResponse.ok(platformService.reject(id, request, authentication.getName()));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Thanh toán của mọi doanh nghiệp, lọc theo trạng thái, doanh nghiệp, ngày tạo (giờ Việt Nam)")
    public ApiResponse<PageResponse<PlatformPayment>> payments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(platformService.payments(status, companyId, from, to, page, size));
    }
}
