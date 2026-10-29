package vn.wms.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.wms.auth.dto.LoginRequest;
import vn.wms.auth.dto.LoginResponse;
import vn.wms.auth.dto.RegisterRequest;
import vn.wms.auth.dto.RegisterResponse;
import vn.wms.auth.dto.UserInfoResponse;
import vn.wms.auth.service.AuthService;
import vn.wms.common.api.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Đăng nhập, JWT và thông tin người dùng hiện tại")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập và nhận JWT")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        return ApiResponse.ok(authService.login(request, clientIp));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo mới tài khoản người dùng (chỉ ADMIN có quyền thực hiện)")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin người dùng hiện tại, lấy từ JWT trong header Authorization")
    public ApiResponse<UserInfoResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.currentUser(authentication));
    }

    @GetMapping("/logout")
    @Operation(summary = "Đăng xuất tài khoản người dùng hiện tại - vô hiệu hóa AccessToken - Xoá refreshToken khỏi cache")
    public ApiResponse<String> logout(@RequestHeader("Authorization") String currentAuthHeader){
        authService.logout(currentAuthHeader);
        return ApiResponse.ok("Đăng xuất tài khoản thành công");
    }
}
