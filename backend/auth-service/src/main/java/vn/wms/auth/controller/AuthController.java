package vn.wms.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.wms.auth.dto.auth.LoginRequest;
import vn.wms.auth.dto.auth.LoginResponse;
import vn.wms.auth.dto.auth.RefreshTokenRequest;
import vn.wms.auth.dto.auth.RefreshTokenResponse;
import vn.wms.auth.dto.auth.RegisterRequest;
import vn.wms.auth.dto.auth.RegisterResponse;
import vn.wms.auth.dto.users.UserInfoResponse;
import vn.wms.auth.service.AuthService;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.exception.ApiException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Đăng nhập, JWT và thông tin người dùng hiện tại")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập và nhận JWT (đồng thời trả Refresh Token trong HttpOnly cookie)")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        if (response.refreshToken() != null) {
            setRefreshTokenCookie(httpResponse, response.refreshToken(), 7 * 24 * 3600);
        }
        return ApiResponse.ok(response);
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

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới Access Token bằng Refresh Token (qua body hoặc HttpOnly cookie)")
    public ApiResponse<RefreshTokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            HttpServletResponse httpResponse) {
        String token = (request != null && request.refreshToken() != null && !request.refreshToken().isBlank())
                ? request.refreshToken()
                : cookieRefreshToken;

        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("Refresh token không được để trống");
        }

        RefreshTokenResponse response = authService.refreshToken(new RefreshTokenRequest(token));
        if (response.refreshToken() != null) {
            setRefreshTokenCookie(httpResponse, response.refreshToken(), 7 * 24 * 3600);
        }
        return ApiResponse.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất tài khoản người dùng hiện tại - vô hiệu hóa AccessToken - Xoá refreshToken khỏi cache và xóa Cookie")
    public ApiResponse<String> logout(
            @RequestHeader("Authorization") String currentAuthHeader,
            HttpServletResponse httpResponse) {
        authService.logout(currentAuthHeader);
        setRefreshTokenCookie(httpResponse, "", 0);
        return ApiResponse.ok("Đăng xuất tài khoản thành công");
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken != null ? refreshToken : "")
                .httpOnly(true)
                .secure(false)
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
