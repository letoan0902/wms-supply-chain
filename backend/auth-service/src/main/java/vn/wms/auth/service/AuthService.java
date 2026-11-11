package vn.wms.auth.service;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.company.dto.CompanySummary;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.dto.auth.LoginRequest;
import vn.wms.auth.dto.auth.LoginResponse;
import vn.wms.auth.dto.auth.RefreshTokenRequest;
import vn.wms.auth.dto.auth.RefreshTokenResponse;
import vn.wms.auth.dto.auth.RegisterRequest;
import vn.wms.auth.dto.auth.RegisterResponse;
import vn.wms.auth.dto.users.UserInfoResponse;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.security.JwtService;
import vn.wms.auth.security.UserPrincipal;
import vn.wms.auth.subscription.dto.SubscriptionSummary;
import vn.wms.auth.subscription.service.SubscriptionService;
import vn.wms.auth.subscription.service.UserPlanLimitGuard;
import vn.wms.common.api.ErrorCode;
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
    private final CompanyRepository companyRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;
    private final UserPlanLimitGuard userPlanLimitGuard;

    public AuthService(JwtService jwtService,
                       DevLoginProperties devLogin,
                       RedisTemplate<String, String> redisTemplate,
                       UserRepository userRepository,
                       RoleRepository roleRepository,
                       CompanyRepository companyRepository,
                       SubscriptionService subscriptionService,
                       PasswordEncoder passwordEncoder,
                       UserPlanLimitGuard userPlanLimitGuard) {
        this.jwtService = jwtService;
        this.devLogin = devLogin;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
        this.passwordEncoder = passwordEncoder;
        this.userPlanLimitGuard = userPlanLimitGuard;
    }

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(30);
    private static final Duration ATTEMPTS_TTL = Duration.ofMinutes(30);

    private static final String LOGIN_ATTEMPTS_IP_PREFIX = "login:attempts:ip:";
    private static final String LOGIN_LOCK_IP_PREFIX = "login:lock:ip:";
    private static final String LOGIN_ATTEMPTS_USER_PREFIX = "login:attempts:user:";
    private static final String LOGIN_LOCK_USER_PREFIX = "login:lock:user:";

    // Không đặt readOnly: bước kiểm tra thuê bao có thể chuyển EXPIRED và phải được lưu lại
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        String identifier = request.username();

        // 1. Tìm người dùng theo username HOẶC email để xác định tên người dùng chuẩn (canonical username)
        User userLogin = userRepository.findByUsernameOrEmail(identifier).orElse(null);
        boolean isUserExist = (userLogin != null);
        String canonicalUsername = isUserExist ? userLogin.getUsername() : identifier;

        // 2. Kiểm tra khóa 2 lớp (IP & Tài khoản) theo canonicalUsername trước khi xác thực mật khẩu
        checkLocks(clientIp, canonicalUsername);

        // 3. So khớp mật khẩu
        if (!isUserExist || !passwordEncoder.matches(request.password(), userLogin.getPasswordHash())) {
            recordFailedAttempt(clientIp, canonicalUsername, isUserExist);
        }

        // 3a. Kiểm tra tài khoản có bị vô hiệu hóa bởi Admin hay không
        if (!userLogin.isEnabled()) {
            throw ApiException.unauthorized("Tài khoản đã bị khóa hoặc ngừng kích hoạt");
        }

        // 3b. Doanh nghiệp của tài khoản phải đang hoạt động (ACTIVE)
        Company company = requireActiveCompany(userLogin);

        // 4. Đăng nhập thành công -> Reset bộ đếm số lần nhập sai của cả IP và Tài khoản
        resetFailedAttempts(clientIp, canonicalUsername);

        // 5. Lấy danh sách tên vai trò thực tế từ database
        List<String> roles = userLogin.getRoles().stream()
                .map(Role::getName)
                .toList();

        // 6. Kiểm tra gói thuê bao: chuyển EXPIRED nếu đã quá hạn và ghi lại bộ nhớ đệm Redis (mục 2.7)
        SubscriptionSummary subscriptionSummary = subscriptionService.refreshForLogin(company.getId())
                .map(SubscriptionSummary::from)
                .orElse(null);
        String plan = subscriptionSummary != null ? subscriptionSummary.plan() : null;

        // 7. Tạo Access Token (kèm companyId, companyCode, plan và warehouseIds) & Refresh Token
        String accessToken = jwtService.generateAccessToken(
                userLogin.getId(), userLogin.getUsername(), company.getId(), company.getCode(), plan,
                roles, userLogin.getWarehouseIds());
        String refreshToken = jwtService.generateRefreshToken(userLogin.getUsername());

        // 8. Lưu Refresh Token vào Redis với TTL
        String key = "refresh:" + userLogin.getUsername();
        redisTemplate.opsForValue().set(key, refreshToken,
                Duration.ofMinutes(jwtService.getRefreshTokenMinutes()));

        return new LoginResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                jwtService.getAccessTokenMinutes() * 60L, // expiresIn (giây)
                userLogin.getUsername(),
                roles,
                CompanySummary.from(company),
                subscriptionSummary);
    }

    /**
     * Trả doanh nghiệp của tài khoản nếu đang ACTIVE; doanh nghiệp chưa kích hoạt, bị tạm khóa
     * hoặc không tồn tại thì từ chối với 403 COMPANY_INACTIVE. Doanh nghiệp chưa kích hoạt qua email (PENDING,
     * WMS-23.6) nhận thông báo riêng kèm {@code data.companyStatus = "PENDING"} để giao diện hiện nút gửi lại email.
     */
    private Company requireActiveCompany(User user) {
        Company company = user.getCompany();
        if (company != null && company.getStatus() == CompanyStatus.PENDING) {
            throw ApiException.withData(ErrorCode.COMPANY_INACTIVE,
                    "Doanh nghiệp chưa được kích hoạt. Vui lòng mở email đăng ký và bấm liên kết kích hoạt; "
                            + "nếu không thấy email, hãy yêu cầu gửi lại email kích hoạt.",
                    Map.of("companyStatus", CompanyStatus.PENDING.name()));
        }
        if (company == null || !company.isActive()) {
            throw new ApiException(ErrorCode.COMPANY_INACTIVE,
                    "Doanh nghiệp của tài khoản chưa được kích hoạt hoặc đang bị tạm khóa. Vui lòng liên hệ quản trị viên của doanh nghiệp.");
        }
        return company;
    }

    private void checkLocks(String clientIp, String username) {
        // Kiểm tra khóa Lớp 1: IP của Client
        String lockIpKey = LOGIN_LOCK_IP_PREFIX + clientIp;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockIpKey))) {
            Long expireSeconds = redisTemplate.getExpire(lockIpKey);
            long remainingMinutes = (expireSeconds != null && expireSeconds > 0) ? (expireSeconds / 60 + 1) : LOCK_TIME.toMinutes();
            throw ApiException.badRequest("Địa chỉ IP của bạn tạm thời bị khóa do đăng nhập sai quá 5 lần. Vui lòng thử lại sau khoảng " + remainingMinutes + " phút");
        }

        // Kiểm tra khóa Lớp 2: Tài khoản người dùng
        String lockUserKey = LOGIN_LOCK_USER_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockUserKey))) {
            Long expireSeconds = redisTemplate.getExpire(lockUserKey);
            long remainingMinutes = (expireSeconds != null && expireSeconds > 0) ? (expireSeconds / 60 + 1) : LOCK_TIME.toMinutes();
            throw ApiException.badRequest("Tài khoản '" + username + "' tạm thời bị khóa do đăng nhập sai quá 5 lần. Vui lòng thử lại sau khoảng " + remainingMinutes + " phút");
        }
    }

    private void recordFailedAttempt(String clientIp, String username, boolean isUserExist) {
        // 1. Tăng bộ đếm cho IP (luôn luôn ghi nhận với mọi request sai)
        String attemptsIpKey = LOGIN_ATTEMPTS_IP_PREFIX + clientIp;
        Long attemptsIp = redisTemplate.opsForValue().increment(attemptsIpKey);
        if (attemptsIp != null && attemptsIp == 1) {
            redisTemplate.expire(attemptsIpKey, ATTEMPTS_TTL);
        }

        // 2. Tăng bộ đếm cho Tài khoản (CHỈ ghi nhận khi username/email THỰC SỰ TỒN TẠI trong DB)
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
            String lockIpKey = LOGIN_LOCK_IP_PREFIX + clientIp;
            redisTemplate.opsForValue().set(lockIpKey, "LOCKED", LOCK_TIME);
            redisTemplate.delete(attemptsIpKey);
            throw ApiException.badRequest("Địa chỉ IP của bạn đã thử sai 5 lần và bị tạm khóa " + LOCK_TIME.toMinutes() + " phút!");
        }

        // 4. Kiểm tra khóa Tài khoản nếu chạm mốc 5 lần
        if (attemptsUser != null && attemptsUser >= MAX_FAILED_ATTEMPTS) {
            String lockUserKey = LOGIN_LOCK_USER_PREFIX + username;
            String attemptsUserKey = LOGIN_ATTEMPTS_USER_PREFIX + username;
            redisTemplate.opsForValue().set(lockUserKey, "LOCKED", LOCK_TIME);
            redisTemplate.delete(attemptsUserKey);
            throw ApiException.badRequest("Tài khoản '" + username + "' đã đăng nhập thất bại 5 lần liên tiếp và bị tạm khóa " + LOCK_TIME.toMinutes() + " phút!");
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
     * Làm mới Access Token bằng Refresh Token (hỗ trợ xoay vòng Refresh Token - RTR).
     */
    // Không đặt readOnly: bước kiểm tra thuê bao có thể chuyển EXPIRED và phải được lưu lại
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        String token = request.refreshToken();
        Claims claims;
        try {
            claims = jwtService.parse(token, redisTemplate);
        } catch (Exception e) {
            throw ApiException.unauthorized("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        // 1. Kiểm tra loại token phải là REFRESH
        String type = claims.get("type", String.class);
        if (!"REFRESH".equals(type)) {
            throw ApiException.unauthorized("Token cung cấp không phải là Refresh Token");
        }

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            throw ApiException.unauthorized("Refresh token không hợp lệ");
        }

        // 2. Đối chiếu với Refresh Token đang lưu trong Redis
        String refreshKey = "refresh:" + username;
        String storedRefreshToken = redisTemplate.opsForValue().get(refreshKey);
        if (storedRefreshToken == null || !storedRefreshToken.equals(token)) {
            throw ApiException.unauthorized("Phiên đăng nhập đã hết hạn hoặc đã được làm mới");
        }

        // 3. Tìm người dùng trong DB và kiểm tra trạng thái kích hoạt
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("Người dùng không tồn tại"));

        if (!user.isEnabled()) {
            throw ApiException.unauthorized("Tài khoản đã bị khóa hoặc ngừng kích hoạt");
        }

        // Doanh nghiệp đọc lại từ cơ sở dữ liệu, phải đang ACTIVE
        Company company = requireActiveCompany(user);

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        // 4. Kiểm tra gói thuê bao: chuyển EXPIRED nếu đã quá hạn và ghi lại bộ nhớ đệm Redis
        String plan = subscriptionService.refreshForLogin(company.getId())
                .map(sub -> sub.getPlan().getCode())
                .orElse(null);

        // 5. Phát hành Access Token mới (kèm companyId, companyCode, plan và warehouseIds)
        String newAccessToken = jwtService.generateAccessToken(
                user.getId(), user.getUsername(), company.getId(), company.getCode(), plan,
                roles, user.getWarehouseIds());

        // 6. Xoay vòng Refresh Token (Refresh Token Rotation - RTR) để nâng cao bảo mật
        String newRefreshToken = jwtService.generateRefreshToken(user.getUsername());
        redisTemplate.opsForValue().set(refreshKey, newRefreshToken,
                Duration.ofMinutes(jwtService.getRefreshTokenMinutes()));

        return new RefreshTokenResponse(
                newAccessToken,
                TOKEN_TYPE,
                jwtService.getAccessTokenMinutes() * 60L,
                newRefreshToken
        );
    }

    /**
     * Tạo mới tài khoản người dùng (chỉ dành cho Admin). Tài khoản mới luôn thuộc doanh nghiệp
     * của ADMIN đang tạo (companyId lấy từ JWT), không nhận doanh nghiệp từ body. Vượt giới hạn số người dùng
     * của gói thì trả {@code PLAN_LIMIT_EXCEEDED} (WMS-23.2).
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request, Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_REQUIRED,
                        "Không tìm thấy doanh nghiệp của tài khoản đang đăng nhập"));

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

        // 4. Giới hạn số người dùng của gói: tài khoản mới luôn ở trạng thái bật
        userPlanLimitGuard.assertCanAddEnabledUser(companyId);

        // 5. Băm mật khẩu bằng BCrypt
        String encodedPassword = passwordEncoder.encode(request.password());

        // 6. Tạo User mới, gán enabled = true và các vai trò
        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setPasswordHash(encodedPassword);
        newUser.setFullName(request.fullName());
        newUser.setEnabled(true);
        newUser.setRoles(assignedRoles);
        newUser.setCompany(company);

        // 7. Lưu vào DB và ghi lại mức sử dụng
        User savedUser = userRepository.save(newUser);
        userPlanLimitGuard.reportUsage(companyId);

        // 8. Trả về RegisterResponse
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
    @Transactional(readOnly = true)
    public UserInfoResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw ApiException.unauthorized("Chưa đăng nhập");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .map(AuthService::stripRolePrefix)
                .toList();
        CompanySummary company = null;
        SubscriptionSummary subscription = null;
        if (authentication.getPrincipal() instanceof UserPrincipal principal && principal.getCompanyId() != null) {
            company = companyRepository.findById(principal.getCompanyId())
                    .map(CompanySummary::from)
                    .orElse(null);
            subscription = subscriptionService.findByCompanyId(principal.getCompanyId())
                    .map(SubscriptionSummary::from)
                    .orElse(null);
        }
        return new UserInfoResponse(authentication.getName(), roles, company, subscription);
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
