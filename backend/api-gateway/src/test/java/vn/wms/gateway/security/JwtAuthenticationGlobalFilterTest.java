package vn.wms.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationGlobalFilterTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-at-least-32-bytes";
    private static final List<String> PUBLIC_PATHS = List.of("/api/auth/login", "/actuator/**");

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    private static JwtAuthenticationGlobalFilter filter(boolean enabled) {
        return new JwtAuthenticationGlobalFilter(new GatewayJwtProperties(enabled, SECRET, PUBLIC_PATHS));
    }

    private static String token(String secret, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("manager01")
                .claim("roles", List.of("WAREHOUSE_MANAGER", "SALES"))
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static MockServerWebExchange exchange(String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path)
                .header("X-User-Name", "ke-gia-mao")
                .header("X-User-Roles", "ADMIN");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return MockServerWebExchange.from(builder);
    }

    @Test
    void whenDisabledPassesThroughButStripsSpoofedIdentityHeaders() {
        MockServerWebExchange exchange = exchange("/api/products", null);

        filter(false).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-User-Name")).isFalse();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-User-Roles")).isFalse();
    }

    @Test
    void whenEnabledRejectsRequestWithoutToken() {
        MockServerWebExchange exchange = exchange("/api/products", null);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void whenEnabledRejectsTokenSignedWithAnotherKey() {
        String forged = token(OTHER_SECRET, Instant.now().plusSeconds(600));
        MockServerWebExchange exchange = exchange("/api/products", "Bearer " + forged);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void whenEnabledRejectsExpiredToken() {
        String expired = token(SECRET, Instant.now().minusSeconds(60));
        MockServerWebExchange exchange = exchange("/api/products", "Bearer " + expired);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void whenEnabledForwardsValidTokenWithIdentityHeaders() {
        String valid = token(SECRET, Instant.now().plusSeconds(600));
        MockServerWebExchange exchange = exchange("/api/products", "Bearer " + valid);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("manager01");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Roles"))
                .isEqualTo("WAREHOUSE_MANAGER,SALES");
    }

    @Test
    void publicPathsSkipValidation() {
        MockServerWebExchange exchange = exchange("/api/auth/login", null);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void enabledFilterRefusesTooShortSecret() {
        GatewayJwtProperties weak = new GatewayJwtProperties(true, "change_me", PUBLIC_PATHS);

        assertThatThrownBy(() -> new JwtAuthenticationGlobalFilter(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
