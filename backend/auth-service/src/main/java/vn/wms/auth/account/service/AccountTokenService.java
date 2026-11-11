package vn.wms.auth.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.account.entity.AccountToken;
import vn.wms.auth.account.entity.AccountTokenType;
import vn.wms.auth.account.repository.AccountTokenRepository;
import vn.wms.auth.apikey.service.ApiKeyService;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Phát và dùng mã một lần trong liên kết gửi qua email (WMS-23.6).
 *
 * <ul>
 *   <li>Mã gồm 32 byte ngẫu nhiên từ {@link SecureRandom}, mã hóa Base64 URL-safe không đệm (43 ký tự).</li>
 *   <li>Chỉ lưu SHA-256 của mã; mã gốc chỉ nằm trong liên kết gửi đi, không ghi log.</li>
 *   <li>Phát mã mới thì các mã cùng loại chưa dùng của cùng doanh nghiệp (kích hoạt) hoặc cùng tài khoản (đặt lại mật
 *       khẩu) bị vô hiệu: chỉ liên kết mới nhất còn dùng được.</li>
 *   <li>Mã sai, đã dùng, sai loại hoặc quá hạn đều trả {@code 400 TOKEN_INVALID} với cùng một thông báo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccountTokenService {

    /** Số byte ngẫu nhiên của mỗi mã. */
    static final int TOKEN_BYTES = 32;
    /** Mã dài hơn mức này chắc chắn sai, từ chối trước khi băm. */
    private static final int MAX_TOKEN_LENGTH = 128;
    static final String INVALID_MESSAGE = "Liên kết không hợp lệ, đã được sử dụng hoặc đã hết hạn";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final AccountTokenRepository repository;

    /** Mã vừa phát: mã gốc (chỉ dùng để dựng liên kết) và thời điểm hết hạn. */
    public record IssuedToken(String rawToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[rawToken=***, expiresAt=" + expiresAt + "]";
        }
    }

    /**
     * Phát mã mới cho doanh nghiệp (và tài khoản nếu có), vô hiệu các mã cùng loại chưa dùng. Phải chạy trong
     * transaction của nghiệp vụ gọi tới để mã chỉ tồn tại khi nghiệp vụ commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedToken issue(AccountTokenType type, Long companyId, Long userId, Duration ttl) {
        Instant now = Instant.now();
        if (type == AccountTokenType.COMPANY_ACTIVATION) {
            repository.revokeOutstandingForCompany(companyId, type, now);
        } else if (userId != null) {
            repository.revokeOutstandingForUser(userId, type, now);
        }
        String rawToken = newRawToken();
        AccountToken token = new AccountToken();
        token.setCompanyId(companyId);
        token.setUserId(userId);
        token.setType(type);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return new IssuedToken(rawToken, token.getExpiresAt());
    }

    /**
     * Dùng mã: khóa dòng, kiểm tra loại, chưa dùng, chưa hết hạn rồi đánh dấu đã dùng. Phải chạy trong transaction
     * của nghiệp vụ (kích hoạt, đặt lại mật khẩu) để lỗi ở bước sau trả mã về trạng thái chưa dùng.
     *
     * @throws ApiException {@code TOKEN_INVALID} khi mã không dùng được
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountToken consume(String rawToken, AccountTokenType expectedType) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
            throw invalid();
        }
        AccountToken token = repository.findByTokenHashForUpdate(hash(rawToken.trim()))
                .orElseThrow(AccountTokenService::invalid);
        Instant now = Instant.now();
        if (token.getType() != expectedType || !token.isUsableAt(now)) {
            throw invalid();
        }
        token.setUsedAt(now);
        return token;
    }

    /** SHA-256 hex chữ thường của mã, dạng lưu trong cột {@code token_hash}. */
    public static String hash(String rawToken) {
        return ApiKeyService.sha256Hex(rawToken);
    }

    static String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    static ApiException invalid() {
        return new ApiException(ErrorCode.TOKEN_INVALID, INVALID_MESSAGE);
    }
}
