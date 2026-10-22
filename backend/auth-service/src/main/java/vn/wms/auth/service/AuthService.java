package vn.wms.auth.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.wms.auth.dto.LoginRequest;
import vn.wms.auth.dto.LoginResponse;
import vn.wms.auth.dto.UserInfoResponse;
import vn.wms.auth.security.JwtService;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ xác thực. Ở giai đoạn khung, đăng nhập là GIẢ LẬP: chấp nhận một số tài khoản dev cố định
 * (mỗi tài khoản ứng với một vai trò) và trả về JWT thật, ký bằng secret lấy từ biến môi trường.
 *
 * <p>TODO (Sprint 1): thay bằng xác thực thật - tìm User theo username trong bảng users, kiểm tra enabled,
 * so khớp mật khẩu bằng PasswordEncoder.matches, lấy vai trò từ bảng user_roles; sau đó tắt đăng nhập dev
 * bằng AUTH_DEV_LOGIN_ENABLED=false.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOKEN_TYPE = "Bearer";

    /** Tài khoản dev: tên đăng nhập -> vai trò, để frontend thử giao diện theo từng vai trò. */
    static final Map<String, String> DEV_ACCOUNTS = Map.of(
            "admin", Roles.ADMIN,
            "manager", Roles.WAREHOUSE_MANAGER,
            "staff", Roles.WAREHOUSE_STAFF,
            "purchasing", Roles.PURCHASING,
            "sales", Roles.SALES,
            "accountant", Roles.ACCOUNTANT);

    private final JwtService jwtService;
    private final DevLoginProperties devLogin;

    public AuthService(JwtService jwtService, DevLoginProperties devLogin) {
        this.jwtService = jwtService;
        this.devLogin = devLogin;
    }

    @PostConstruct
    void warnWhenDevLoginEnabled() {
        if (devLogin.enabled()) {
            log.warn("Đăng nhập DEV đang BẬT (app.auth.dev-login.enabled=true): chỉ dùng cho phát triển, "
                    + "đặt AUTH_DEV_LOGIN_ENABLED=false ở môi trường thật");
        }
    }

    public LoginResponse login(LoginRequest request) {
        if (!devLogin.enabled()) {
            throw ApiException.unauthorized("Đăng nhập chưa được cấu hình");
        }
        String role = DEV_ACCOUNTS.get(request.username());
        if (role == null || !constantTimeEquals(devLogin.password(), request.password())) {
            throw ApiException.unauthorized("Sai tên đăng nhập hoặc mật khẩu");
        }
        List<String> roles = List.of(role);
        String token = jwtService.generateToken(request.username(), roles);
        return new LoginResponse(token, TOKEN_TYPE, jwtService.expiresInSeconds(), request.username(), roles);
    }

    /** Thông tin người dùng hiện tại, dựng từ Authentication mà JwtAuthenticationFilter đã đặt. */
    public UserInfoResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw ApiException.unauthorized("Chưa đăng nhập");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .map(AuthService::stripRolePrefix)
                .toList();
        return new UserInfoResponse(authentication.getName(), roles);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String stripRolePrefix(String authority) {
        return authority.startsWith(Roles.AUTHORITY_PREFIX)
                ? authority.substring(Roles.AUTHORITY_PREFIX.length())
                : authority;
    }
}
