package vn.wms.auth.apikey.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.apikey.dto.ApiKeyResponse;
import vn.wms.auth.apikey.dto.CreateApiKeyRequest;
import vn.wms.auth.apikey.dto.CreatedApiKeyResponse;
import vn.wms.auth.apikey.service.ApiKeyService;
import vn.wms.auth.security.UserPrincipal;
import vn.wms.common.api.ApiResponse;

import java.util.List;

/**
 * Quản lý API key cho hệ thống ngoài (docs/SAAS_SPEC.md, WMS-25.1). Chỉ ADMIN, chỉ thấy key của doanh nghiệp mình;
 * key của doanh nghiệp khác trả 404.
 */
@RestController
@RequestMapping("/api/auth/api-keys")
@Tag(name = "API Key", description = "API key cho hệ thống ngoài đọc hàng hóa, tồn kho, đơn hàng")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo API key (chỉ gói có quyền dùng API, tối đa 5 key đang hoạt động); key đầy đủ chỉ trả đúng lần này")
    public ApiResponse<CreatedApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request,
                                                     Authentication authentication) {
        Long companyId = UserPrincipal.requireCompanyId(authentication);
        Long userId = authentication.getPrincipal() instanceof UserPrincipal principal ? principal.getId() : null;
        return ApiResponse.ok(apiKeyService.create(companyId, userId, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách API key của doanh nghiệp (không kèm key), mới nhất trước")
    public ApiResponse<List<ApiKeyResponse>> list(Authentication authentication) {
        return ApiResponse.ok(apiKeyService.list(UserPrincipal.requireCompanyId(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thu hồi API key; hệ thống ngoài dùng key này nhận 401 ngay sau đó")
    public ApiResponse<ApiKeyResponse> revoke(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(apiKeyService.revoke(UserPrincipal.requireCompanyId(authentication), id));
    }
}
