package vn.wms.auth.service;

import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.hibernate5.SpringSessionContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.dto.LoginRequest;
import vn.wms.auth.dto.LoginResponse;
import vn.wms.auth.dto.RegisterRequest;
import vn.wms.auth.dto.RegisterResponse;
import vn.wms.auth.dto.UserInfoResponse;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.exceptions.UserNotExistedException;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.security.JwtService;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ xác thực: tìm User theo username trong bảng users, kiểm tra enabled,
 * so khớp mật khẩu bằng PasswordEncoder.matches, lấy vai trò từ bảng user_roles và phát hành JWT.
 */
@Service
@Slf4j
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private final JwtService jwtService;
    private final DevLoginProperties devLogin;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JwtService jwtService,
                       DevLoginProperties devLogin,
                       RedisTemplate<String, String> redisTemplate,
                       UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.devLogin = devLogin;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(15);
    private static final Duration ATTEMPTS_TTL = Duration.ofMinutes(15);

    private static final String LOGIN_ATTEMPTS_IP_PREFIX = "login:attempts:ip:";
    private static final String LOGIN_LOCK_IP_PREFIX = "login:lock:ip:";
    private static final String LOGIN_ATTEMPTS_USER_PREFIX = "login:attempts:user:";
    private static final String LOGIN_LOCK_USER_PREFIX = "login:lock:user:";

//    @Transactional(readOnly = true)
//    public LoginResponse login(LoginRequest request) {
//        return login(request, "127.0.0.1");
//    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username();

        // 1. Kiểm tra khóa 2 lớp (IP & Tài khoản) trước khi xử lý
        checkLocks(clientIp, username);

        // 2. Tìm người dùng và so khớp mật khẩu
        User userLogin = userRepository.findByUsername(username).orElse(null);
        boolean isUserExist = (userLogin != null);

        if (!isUserExist || !passwordEncoder.matches(request.password(), userLogin.getPasswordHash())) {
            recordFailedAttempt(clientIp, username, isUserExist);
        }

        // 3. Kiểm tra tài khoản có bị vô hiệu hóa bởi Admin hay không
        if (!userLogin.isEnabled()) {
            throw ApiException.unauthorized("Tài khoản đã bị khóa hoặc ngừng kích hoạt");
        }

        // 4. Đăng nhập thành công -> Reset bộ đếm số lần nhập sai của cả IP và Tài khoản
        resetFailedAttempts(clientIp, username);

        // 5. Lấy danh sách tên vai trò thực tế từ database
        List<String> roles = userLogin.getRoles().stream()
                .map(Role::getName)
                .toList();

        // 6. Tạo Access Token & Refresh Token
        String accessToken = jwtService.generateAccessToken(userLogin.getId(), userLogin.getUsername(), roles);
        String refreshToken = jwtService.generateRefreshToken(userLogin.getUsername());

        // 7. Lưu Refresh Token vào Redis với TTL
        String key = "refresh:" + userLogin.getUsername();
        redisTemplate.opsForValue().set(key, refreshToken,
                Duration.ofMinutes(jwtService.getRefreshTokenMinutes()));

        return new LoginResponse(
                accessToken,
                TOKEN_TYPE,
                jwtService.getAccessTokenMinutes() * 60L, // expiresIn (giây)
                userLogin.getUsername(),
                roles);
    }

    private void checkLocks(String clientIp, String username) {
        // Kiểm tra khóa Lớp 1: IP của Client
        String lockIpKey = LOGIN_LOCK_IP_PREFIX + clientIp;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockIpKey))) {
            Long expireSeconds = redisTemplate.getExpire(lockIpKey);
            long remainingMinutes = (expireSeconds != null && expireSeconds > 0) ? (expireSeconds / 60 + 1) : 15;
            throw ApiException.badRequest("Địa chỉ IP của bạn tạm thời bị khóa do đăng nhập sai quá 5 lần. Vui lòng thử lại sau khoảng " + remainingMinutes + " phút");
        }

        // Kiểm tra khóa Lớp 2: Tài khoản người dùng
        String lockUserKey = LOGIN_LOCK_USER_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockUserKey))) {
            Long expireSeconds = redisTemplate.getExpire(lockUserKey);
            long remainingMinutes = (expireSeconds != null && expireSeconds > 0) ? (expireSeconds / 60 + 1) : 15;
            throw ApiException.badRequest("Tài khoản '" + username + "' tạm thời bị khóa do đăng nhập sai quá 5 lần. Vui lòng thử lại sau khoảng " + remainingMinutes + " phút");
        }
    }

    private void recordFailedAttempt(String clientIp, String username, boolean isUserExist) {
        // 1. Tăng bộ đếm cho IP (luôn luôn ghi nhận với mọi request sai)
        String attemptsIpKey = LOGIN_ATTEMPTS_IP_PREFIX + clientIp;
        String lockIpKey = LOGIN_LOCK_IP_PREFIX + clientIp;
        Long attemptsIp = redisTemplate.opsForValue().increment(attemptsIpKey);
        if (attemptsIp != null && attemptsIp == 1) {
            redisTemplate.expire(attemptsIpKey, ATTEMPTS_TTL);
        }

        // 2. Tăng bộ đếm cho Tài khoản (khi username thực sự tồn tại trong DB)
        Long attemptsUser = null;
        if (isUserExist) {
            String attemptsUserKey = LOGIN_ATTEMPTS_USER_PREFIX + username;
            attemptsUser = redisTemplate.opsForValue().increment(attemptsUserKey);
            if (attemptsUser != null && attemptsUser == 1) {
                redisTemplate.expire(attemptsUserKey, ATTEMPTS_TTL);
            }
        }

        // 3. Kiểm tra khóa IP nếu chạm mốc 5 lần
        if (attemptsIp != null && attemptsIp >= MAX_FAILED_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockIpKey, "LOCKED", LOCK_TIME);
            redisTemplate.delete(attemptsIpKey);
            throw ApiException.badRequest("Bạn đã đăng nhập thất bại 5 lần liên tiếp. Vui lòng thử lại sau 15 phút!");
        }

        // 4. Kiểm tra khóa Tài khoản nếu chạm mốc 5 lần
        if (attemptsUser != null && attemptsUser >= MAX_FAILED_ATTEMPTS) {
            String lockUserKey = LOGIN_LOCK_USER_PREFIX + username;
            String attemptsUserKey = LOGIN_ATTEMPTS_USER_PREFIX + username;
            redisTemplate.opsForValue().set(lockUserKey, "LOCKED", LOCK_TIME);
            redisTemplate.delete(attemptsUserKey);
            throw ApiException.badRequest("Tài khoản '" + username + "' đã đăng nhập thất bại 5 lần liên tiếp và bị tạm khóa 15 phút!");
        }

        // 5. Tính số lượt thử còn lại theo mức nghiêm ngặt nhất (MIN giữa IP và Tài khoản)
        long remainingIp = MAX_FAILED_ATTEMPTS - (attemptsIp != null ? attemptsIp : 1);
        long remaining = remainingIp;
        if (attemptsUser != null) {
            long remainingUser = MAX_FAILED_ATTEMPTS - attemptsUser;
            remaining = Math.min(remainingIp, remainingUser);
        }

        throw ApiException.unauthorized("Tên đăng nhập hoặc mật khẩu chưa chính xác. Bạn còn " + remaining + " lần thử");
    }

    private void resetFailedAttempts(String clientIp, String username) {
        redisTemplate.delete(LOGIN_ATTEMPTS_IP_PREFIX + clientIp);
        redisTemplate.delete(LOGIN_ATTEMPTS_USER_PREFIX + username);
    }

    public void logout(String authHeader){
        if (authHeader == null || !authHeader.startsWith(TOKEN_TYPE + " ")){
            throw ApiException.badRequest("Token không hợp lệ");
        }

        String token = authHeader.substring(TOKEN_TYPE.length() + 1);
        // giải mã Claims --> token, username, expiresTime
        Claims claims = jwtService.parse(token, redisTemplate);
        String username = claims.getSubject();
        Date exp = claims.getExpiration();

        // Tính thời gian sống còn lại
        long remainingMilis = exp.getTime() - System.currentTimeMillis();
        if(remainingMilis > 0){
            String expAccessToken = "blacklist:" + token;
            redisTemplate.opsForValue().set(expAccessToken, "logout", Duration.ofMillis(remainingMilis));
        }

        // remove refreshToken
        String refreshKey =  "refresh:" + username;
        redisTemplate.delete(refreshKey);

        // clean context
        SecurityContextHolder.clearContext();
    }

    /**
     * Tạo mới tài khoản người dùng (chỉ dành cho Admin / IT).
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Kiểm tra trùng lặp tên đăng nhập
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("Tên đăng nhập '" + request.username() + "' đã tồn tại");
        }

        // 2. Kiểm tra trùng lặp email
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email '" + request.email() + "' đã tồn tại");
        }

        // 3. Kiểm tra tính hợp lệ của vai trò và tìm trong DB
        Set<Role> assignedRoles = new HashSet<>();
        for (String roleName : request.roles()) {
            if (!Roles.isValid(roleName)) {
                throw ApiException.badRequest("Vai trò không hợp lệ: " + roleName);
            }
            Role roleEntity = roleRepository.findByName(roleName)
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy vai trò '" + roleName + "' trong hệ thống"));
            assignedRoles.add(roleEntity);
        }

        // 4. Băm mật khẩu bằng BCrypt
        String encodedPassword = passwordEncoder.encode(request.password());

        // 5. Tạo User mới, gán enabled = true và các vai trò
        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setPasswordHash(encodedPassword);
        newUser.setFullName(request.fullName());
        newUser.setEnabled(true);
        newUser.setRoles(assignedRoles);

        // 6. Lưu vào DB
        User savedUser = userRepository.save(newUser);

        // 7. Trả về RegisterResponse
        Set<String> roleNames = savedUser.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.isEnabled(),
                roleNames,
                savedUser.getCreatedAt()
        );
    }

    /**
     * Thông tin người dùng hiện tại, dựng từ Authentication mà
     * JwtAuthenticationFilter đã đặt.
     */
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
