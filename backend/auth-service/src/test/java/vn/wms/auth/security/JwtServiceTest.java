package vn.wms.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-at-least-32-bytes";
    private static final String ISSUER = "wms-auth-service";

    private static JwtService service(String secret, long expirationMinutes) {
        return new JwtService(new JwtProperties(secret, expirationMinutes, ISSUER));
    }

    @Test
    void generatedTokenCanBeParsedBack() {
        JwtService jwt = service(SECRET, 60);

        String token = jwt.generateToken("manager", List.of("WAREHOUSE_MANAGER", "SALES"));
        Claims claims = jwt.parse(token);

        assertThat(claims.getSubject()).isEqualTo("manager");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(jwt.rolesOf(claims)).containsExactly("WAREHOUSE_MANAGER", "SALES");
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(jwt.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String foreignToken = service(OTHER_SECRET, 60).generateToken("admin", List.of("ADMIN"));

        assertThatThrownBy(() -> service(SECRET, 60).parse(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(SECRET, -1);
        String expired = jwt.generateToken("admin", List.of("ADMIN"));

        assertThatThrownBy(() -> jwt.parse(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(SECRET, 60);
        String token = jwt.generateToken("staff", List.of("WAREHOUSE_STAFF"));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan32BytesIsRefused() {
        assertThatThrownBy(() -> service("change_me", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
