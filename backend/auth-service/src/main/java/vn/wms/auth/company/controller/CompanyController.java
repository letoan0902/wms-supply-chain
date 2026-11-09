package vn.wms.auth.company.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.company.dto.CompanyResponse;
import vn.wms.auth.company.dto.UpdateCompanyRequest;
import vn.wms.auth.company.service.CompanyService;
import vn.wms.auth.security.UserPrincipal;
import vn.wms.common.api.ApiResponse;

/**
 * Doanh nghiệp của người đang đăng nhập. Không có API liệt kê, xem theo ID hay sửa doanh nghiệp khác:
 * mọi thao tác dùng companyId trong JWT. Tạo doanh nghiệp thuộc WMS-23.1 (đăng ký).
 */
@RestController
@RequestMapping("/api/auth/company")
@RequiredArgsConstructor
@Tag(name = "Company", description = "Thông tin doanh nghiệp của người dùng đang đăng nhập")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem thông tin doanh nghiệp của người dùng đang đăng nhập")
    public ApiResponse<CompanyResponse> getMyCompany(Authentication authentication) {
        return ApiResponse.ok(companyService.getOwnCompany(UserPrincipal.requireCompanyId(authentication)));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN cập nhật tên, mã số thuế, số điện thoại, địa chỉ của doanh nghiệp mình")
    public ApiResponse<CompanyResponse> updateMyCompany(
            @Valid @RequestBody UpdateCompanyRequest request,
            Authentication authentication) {
        return ApiResponse.ok(companyService.updateOwnCompany(UserPrincipal.requireCompanyId(authentication), request));
    }
}
