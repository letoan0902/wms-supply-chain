package vn.wms.auth.company.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.wms.auth.company.dto.CompanyResponse;
import vn.wms.auth.company.dto.CreateCompanyRequest;
import vn.wms.auth.company.dto.UpdateCompanyRequest;
import vn.wms.auth.company.service.CompanyService;
import vn.wms.auth.security.UserPrincipal;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;

@RestController
@RequestMapping({"/api/auth/companies", "/api/companies"})
@RequiredArgsConstructor
@Tag(name = "Company", description = "Quản lý doanh nghiệp (SaaS Tenants)")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "System Admin: Lấy danh sách doanh nghiệp có phân trang và tìm kiếm")
    public ApiResponse<PageResponse<CompanyResponse>> listCompanies(
            @RequestParam(required = false) String keyword,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication
    ) {
        UserPrincipal principal = getPrincipal(authentication);
        if (!isSystemAdmin(principal)) {
            throw ApiException.forbidden("Chỉ Quản trị viên hệ thống (System Admin) mới có quyền xem toàn bộ doanh nghiệp");
        }
        return ApiResponse.ok(companyService.listCompanies(keyword, pageable));
    }

    @GetMapping("/me")
    @Operation(summary = "Xem thông tin doanh nghiệp của người dùng hiện tại đang đăng nhập")
    public ApiResponse<CompanyResponse> getMyCompany(Authentication authentication) {
        UserPrincipal principal = getPrincipal(authentication);
        if (principal.getCompanyId() == null) {
            throw ApiException.badRequest("Tài khoản Quản trị hệ thống không gắn với doanh nghiệp cụ thể");
        }
        return ApiResponse.ok(companyService.getCompanyById(principal.getCompanyId(), principal.getCompanyId(), false));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xem thông tin chi tiết một doanh nghiệp theo ID")
    public ApiResponse<CompanyResponse> getCompanyById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        UserPrincipal principal = getPrincipal(authentication);
        return ApiResponse.ok(companyService.getCompanyById(id, principal.getCompanyId(), isSystemAdmin(principal)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "System Admin: Tạo mới một doanh nghiệp thủ công")
    public ApiResponse<CompanyResponse> createCompany(
            @Valid @RequestBody CreateCompanyRequest request,
            Authentication authentication
    ) {
        UserPrincipal principal = getPrincipal(authentication);
        if (!isSystemAdmin(principal)) {
            throw ApiException.forbidden("Chỉ Quản trị viên hệ thống mới có quyền tạo doanh nghiệp mới");
        }
        return ApiResponse.ok(companyService.createCompany(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật thông tin doanh nghiệp")
    public ApiResponse<CompanyResponse> updateCompany(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCompanyRequest request,
            Authentication authentication
    ) {
        UserPrincipal principal = getPrincipal(authentication);
        return ApiResponse.ok(companyService.updateCompany(id, request, principal.getCompanyId(), isSystemAdmin(principal)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "System Admin: Khóa hoặc kích hoạt lại trạng thái doanh nghiệp")
    public ApiResponse<CompanyResponse> toggleStatus(
            @PathVariable Long id,
            @RequestParam String status,
            Authentication authentication
    ) {
        UserPrincipal principal = getPrincipal(authentication);
        if (!isSystemAdmin(principal)) {
            throw ApiException.forbidden("Chỉ Quản trị viên hệ thống mới có quyền thay đổi trạng thái doanh nghiệp");
        }
        return ApiResponse.ok(companyService.toggleCompanyStatus(id, status));
    }

    private UserPrincipal getPrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw ApiException.unauthorized("Chưa đăng nhập hoặc thiếu thông tin xác thực");
    }

    private boolean isSystemAdmin(UserPrincipal principal) {
        boolean hasAdminRole = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Roles.toAuthority(Roles.ADMIN)));
        return hasAdminRole && principal.getCompanyId() == null;
    }
}
