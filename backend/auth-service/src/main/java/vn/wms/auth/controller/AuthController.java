package vn.wms.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.dto.LoginRequest;
import vn.wms.auth.dto.LoginResponse;
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
    @Operation(summary = "Đăng nhập và nhận JWT (khung: tài khoản dev admin/manager/staff/purchasing/sales/accountant)")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin người dùng hiện tại, lấy từ JWT trong header Authorization")
    public ApiResponse<UserInfoResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.currentUser(authentication));
    }
}
