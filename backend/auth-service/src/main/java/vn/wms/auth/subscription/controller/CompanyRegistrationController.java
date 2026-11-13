package vn.wms.auth.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.company.dto.AdminCompanyResponse;
import vn.wms.auth.company.dto.ApproveCompanyRequest;
import vn.wms.auth.company.dto.RejectCompanyRequest;
import vn.wms.auth.company.dto.UpdateCompanyStatusRequest;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.company.service.AdminCompanyService;
import vn.wms.auth.subscription.dto.CompanyRegistrationResponse;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.service.CompanyRegistrationService;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;

@RestController
@RequestMapping("/api/auth/companies")
@RequiredArgsConstructor
@Tag(name = "Company Management & Registration", description = "Đăng ký doanh nghiệp và quản lý toàn bộ công ty khách hàng")
public class CompanyRegistrationController {

    private final CompanyRegistrationService registrationService;
    private final AdminCompanyService adminCompanyService;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký doanh nghiệp mới (chờ Quản trị viên phê duyệt)")
    public ResponseEntity<ApiResponse<CompanyRegistrationResponse>> register(@Valid @RequestBody RegisterCompanyRequest request) {
        CompanyRegistrationResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin lấy danh sách doanh nghiệp (phân trang, lọc theo trạng thái, tìm kiếm từ khóa)")
    public ApiResponse<PageResponse<AdminCompanyResponse>> getCompanies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CompanyStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(adminCompanyService.getCompanies(keyword, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin xem chi tiết thông tin doanh nghiệp, tài khoản và gói dịch vụ")
    public ApiResponse<AdminCompanyResponse> getCompanyDetail(@PathVariable Long id) {
        return ApiResponse.ok(adminCompanyService.getCompanyDetail(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin phê duyệt doanh nghiệp, kích hoạt tài khoản Admin và gửi email bàn giao")
    public ApiResponse<AdminCompanyResponse> approveCompany(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveCompanyRequest request) {
        return ApiResponse.ok(adminCompanyService.approveCompany(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin từ chối đăng ký doanh nghiệp và gửi email thông báo lý do")
    public ApiResponse<AdminCompanyResponse> rejectCompany(
            @PathVariable Long id,
            @Valid @RequestBody RejectCompanyRequest request) {
        return ApiResponse.ok(adminCompanyService.rejectCompany(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin khóa hoặc mở khóa trạng thái hoạt động của doanh nghiệp")
    public ApiResponse<AdminCompanyResponse> updateCompanyStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCompanyStatusRequest request) {
        return ApiResponse.ok(adminCompanyService.updateStatus(id, request));
    }
}
