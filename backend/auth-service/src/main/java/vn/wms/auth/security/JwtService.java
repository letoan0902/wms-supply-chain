package vn.wms.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Tạo và kiểm tra JWT bằng jjwt (HS256). Secret đọc từ cấu hình (biến môi
 * trường JWT_SECRET).
 *
 * <p>
 * Claim sử dụng: sub = tên đăng nhập, roles = danh sách vai trò (không tiền tố
 * ROLE_), iss, iat, exp.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) phải dài tối thiểu 32 byte (256 bit) cho thuật toán HS256");
        }
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Tạo Access Token (loại ACCESS) cho người dùng.
     * Token chứa username, roles và claim "type":"ACCESS".
     */
    public String generateAccessToken(Long userId, String username, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(username)
                .claim("userId", userId)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim("type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(properties.accessTokenMinutes()))))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Tạo Refresh Token (loại REFRESH) cho người dùng.
     * Token chỉ chứa username và claim "type":"REFRESH".
     */
    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(username)
                .claim("type", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(properties.refreshTokenMinutes()))))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Tạo token đơn giản cho username và roles (tương thích ngược cho test và service khác).
     */
    public String generateToken(String username, Collection<String> roles) {
        return generateAccessToken(null, username, roles);
    }

    /**
     * Kiểm tra chữ ký, issuer và hạn dùng rồi trả về claims (không kiểm tra blacklist).
     */
    public Claims parse(String token) {
        return parse(token, null);
    }

    /**
     * Kiểm tra token có bị blacklist (đăng xuất) không và trả về Claims.
     * Nếu token nằm trong blacklist, ném JwtException.
     */
    public Claims parse(String token, org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate) {
        // Kiểm tra blacklist nếu redisTemplate được cung cấp
        if (redisTemplate != null) {
            Boolean blacklisted = redisTemplate.hasKey("blacklist:" + token);
            if (Boolean.TRUE.equals(blacklisted)) {
                throw new JwtException("Token đã bị blacklist (đăng xuất)");
            }
        }
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Lấy danh sách vai trò trong claims (rỗng nếu token không có claim roles). */
    public List<String> rolesOf(Claims claims) {
        List<?> roles = claims.get(CLAIM_ROLES, List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream().map(String::valueOf).toList();
    }

    /**
     * Thời gian sống của token tính bằng giây (trả về cho client trong trường
     * expiresIn).
     */
    public long expiresInSeconds() {
        return Duration.ofMinutes(properties.expirationMinutes()).toSeconds();
    }

    /** Return the configured access token lifespan (minutes) */
    public long getAccessTokenMinutes() {
        return properties.accessTokenMinutes();
    }

    /** Return the configured refresh token lifespan (minutes) */
    public long getRefreshTokenMinutes() {
        return properties.refreshTokenMinutes();
    }
}
