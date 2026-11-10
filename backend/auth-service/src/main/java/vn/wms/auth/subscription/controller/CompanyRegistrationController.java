package vn.wms.auth.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.subscription.dto.CompanyRegistrationResponse;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.service.CompanyRegistrationService;
import vn.wms.common.api.ApiResponse;

@RestController
@RequestMapping("/api/auth/companies")
@RequiredArgsConstructor
@Tag(name = "Company Registration", description = "API đăng ký doanh nghiệp mới và dùng thử SaaS")
public class CompanyRegistrationController {

    private final CompanyRegistrationService registrationService;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký doanh nghiệp mới, tài khoản Admin đầu tiên và 30 ngày dùng thử (TRIAL)")
    public ResponseEntity<ApiResponse<CompanyRegistrationResponse>> register(@Valid @RequestBody RegisterCompanyRequest request) {
        CompanyRegistrationResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
