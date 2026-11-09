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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Tạo và kiểm tra JWT bằng jjwt (HS256). Secret đọc từ cấu hình (biến môi
 * trường JWT_SECRET).
 *
 * <p>
 * Claim sử dụng: sub = tên đăng nhập, userId, roles = danh sách vai trò, warehouseIds = danh sách kho,
 * companyId = ID doanh nghiệp, companyCode = mã doanh nghiệp, type, iss, iat, exp.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_WAREHOUSE_IDS = "warehouseIds";
    public static final String CLAIM_COMPANY_ID = "companyId";
    public static final String CLAIM_COMPANY_CODE = "companyCode";
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
     * Tạo Access Token cho người dùng không kèm kho hay doanh nghiệp (tương thích ngược).
     */
    public String generateAccessToken(Long userId, String username, Collection<String> roles) {
        return generateAccessToken(userId, username, null, null, roles, Collections.emptyList());
    }

    /**
     * Tạo Access Token cho người dùng kèm danh sách kho, không kèm doanh nghiệp (tương thích ngược).
     */
    public String generateAccessToken(Long userId, String username, Collection<String> roles, Collection<Long> warehouseIds) {
        return generateAccessToken(userId, username, null, null, roles, warehouseIds);
    }

    /**
     * Tạo Access Token kèm companyId nhưng không kèm mã doanh nghiệp (tương thích ngược).
     */
    public String generateAccessToken(Long userId, String username, Long companyId, Collection<String> roles, Collection<Long> warehouseIds) {
        return generateAccessToken(userId, username, companyId, null, roles, warehouseIds);
    }

    /**
     * Tạo Access Token đầy đủ: userId, username, companyId, companyCode, roles, warehouseIds, type = ACCESS.
     */
    public String generateAccessToken(Long userId, String username, Long companyId, String companyCode,
                                      Collection<String> roles, Collection<Long> warehouseIds) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(username)
                .claim("userId", userId)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_WAREHOUSE_IDS, warehouseIds != null ? List.copyOf(warehouseIds) : List.of())
                .claim("type", "ACCESS");

        if (companyId != null) {
            builder.claim(CLAIM_COMPANY_ID, companyId);
        }
        if (companyCode != null) {
            builder.claim(CLAIM_COMPANY_CODE, companyCode);
        }

        return builder.issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(properties.accessTokenMinutes()))))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Tạo Refresh Token (loại REFRESH) cho người dùng.
     */
    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(username)
                .claim("type", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(properties.refreshTokenMinutes()))))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Tạo token đơn giản cho username và roles.
     */
    public String generateToken(String username, Collection<String> roles) {
        return generateAccessToken(null, username, roles);
    }

    /**
     * Kiểm tra chữ ký, issuer và hạn dùng rồi trả về claims.
     */
    public Claims parse(String token) {
        return parse(token, null);
    }

    /**
     * Kiểm tra chữ ký, issuer, hạn dùng và blacklist Redis rồi trả về claims.
     */
    public Claims parse(String token, org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate) {
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

    /** Đọc tên đăng nhập từ claims. */
    public String usernameOf(Claims claims) {
        return claims.getSubject();
    }

    /** Lấy companyId từ claims, không có thì trả null. */
    public Long companyIdOf(Claims claims) {
        Object value = claims.get(CLAIM_COMPANY_ID);
        if (value == null) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }

    /** Lấy mã doanh nghiệp từ claims, không có thì trả null. */
    public String companyCodeOf(Claims claims) {
        return claims.get(CLAIM_COMPANY_CODE, String.class);
    }

    /** Lấy danh sách vai trò từ claims. */
    public List<String> rolesOf(Claims claims) {
        List<?> roles = claims.get(CLAIM_ROLES, List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream().map(String::valueOf).toList();
    }

    /** Lấy danh sách ID các kho trong claims. */
    public List<Long> warehouseIdsOf(Claims claims) {
        List<?> list = claims.get(CLAIM_WAREHOUSE_IDS, List.class);
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .map(item -> item instanceof Number n ? n.longValue() : Long.parseLong(item.toString()))
                .toList();
    }

    public long expiresInSeconds() {
        return Duration.ofMinutes(properties.expirationMinutes()).toSeconds();
    }

    public long getAccessTokenMinutes() {
        return properties.accessTokenMinutes();
    }

    public long getRefreshTokenMinutes() {
        return properties.refreshTokenMinutes();
    }
}
