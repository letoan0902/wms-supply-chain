package vn.wms.auth.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.account.entity.AccountToken;
import vn.wms.auth.account.entity.AccountTokenType;
import vn.wms.auth.account.repository.AccountTokenRepository;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Quên và đặt lại mật khẩu bằng liên kết dùng một lần (WMS-23.6).
 *
 * <p>Chỉ tài khoản đang bật của doanh nghiệp đang {@code ACTIVE} nhận liên kết (hiệu lực 30 phút). Đặt lại thành công
 * thì đổi mật khẩu, vô hiệu mọi liên kết đặt lại khác của tài khoản và xóa refresh token đang lưu trên Redis
 * ({@code refresh:{username}}, mỗi tài khoản một refresh token): mọi phiên đang mở phải đăng nhập lại khi access token
 * hiện tại (15 phút) hết hạn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    /** Khóa Redis của refresh token, cùng quy ước với {@code AuthService}. */
    static final String REFRESH_KEY_PREFIX = "refresh:";

    private final AccountTokenService tokenService;
    private final AccountTokenRepository tokenRepository;
    private final AccountEmailPublisher emailPublisher;
    private final AccountEmailProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    /** Gửi liên kết đặt lại nếu email thuộc tài khoản dùng được; trường hợp khác không làm gì. */
    @Transactional
    public void sendResetLink(String normalizedEmail) {
        Optional<User> found = userRepository.findByEmail(normalizedEmail);
        if (found.isEmpty() || !canSignIn(found.get())) {
            return;
        }
        User user = found.get();
        AccountTokenService.IssuedToken token = tokenService.issue(AccountTokenType.PASSWORD_RESET,
                user.getCompany().getId(), user.getId(), properties.passwordResetTtl());
        emailPublisher.publishPasswordReset(user.getCompany(), user, token);
    }

    /**
     * Đặt mật khẩu mới bằng mã trong liên kết. Mã sai, đã dùng, quá 30 phút, hoặc tài khoản đã bị khóa, doanh nghiệp
     * không còn hoạt động từ lúc gửi liên kết: {@code 400 TOKEN_INVALID}.
     */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        AccountToken token = tokenService.consume(rawToken, AccountTokenType.PASSWORD_RESET);
        User user = Optional.ofNullable(token.getUserId())
                .flatMap(userRepository::findById)
                .orElseThrow(AccountTokenService::invalid);
        if (!canSignIn(user)) {
            throw AccountTokenService.invalid();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.revokeOutstandingForUser(user.getId(), AccountTokenType.PASSWORD_RESET, Instant.now());
        // Vô hiệu refresh token của tài khoản: phiên cũ (kể cả của người đã biết mật khẩu cũ) không làm mới được nữa
        redisTemplate.delete(REFRESH_KEY_PREFIX + user.getUsername());
        log.info("Tài khoản {} đã đặt lại mật khẩu bằng liên kết email (companyId={})", user.getUsername(),
                user.getCompany().getId());
    }

    private static boolean canSignIn(User user) {
        Company company = user.getCompany();
        return user.isEnabled() && company != null && company.isActive();
    }
}
