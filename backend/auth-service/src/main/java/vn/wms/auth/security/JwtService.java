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
 * Tạo và kiểm tra JWT bằng jjwt (HS256). Secret đọc từ cấu hình (biến môi trường JWT_SECRET).
 *
 * <p>Claim sử dụng: sub = tên đăng nhập, roles = danh sách vai trò (không tiền tố ROLE_), iss, iat, exp.
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

    /** Phát hành access token cho người dùng với danh sách vai trò. */
    public String generateToken(String username, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(username)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(properties.expirationMinutes()))))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Kiểm tra chữ ký, issuer và hạn dùng rồi trả về claims.
     *
     * @throws JwtException nếu token sai định dạng, sai chữ ký, sai issuer hoặc đã hết hạn
     */
    public Claims parse(String token) {
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

    /** Thời gian sống của token tính bằng giây (trả về cho client trong trường expiresIn). */
    public long expiresInSeconds() {
        return Duration.ofMinutes(properties.expirationMinutes()).toSeconds();
    }
}
