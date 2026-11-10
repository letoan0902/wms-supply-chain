package vn.wms.gateway.security;

import io.jsonwebtoken.JwtBuilder;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationGlobalFilterTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-at-least-32-bytes";
    private static final List<String> PUBLIC_PATHS = List.of("/api/auth/login", "/actuator/**", "/ws-wms/**");

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    private static JwtAuthenticationGlobalFilter filter(boolean enabled) {
        return filter(enabled, true);
    }

    private static JwtAuthenticationGlobalFilter filter(boolean enabled, boolean requireCompany) {
        return new JwtAuthenticationGlobalFilter(
                new GatewayJwtProperties(enabled, SECRET, PUBLIC_PATHS, requireCompany));
    }

    private static String token(String secret, Instant expiresAt) {
        return token(secret, expiresAt, 7L);
    }

    /** companyId null thì token không có claim này, giống token cấp trước khi hệ thống có nhiều doanh nghiệp. */
    private static String token(String secret, Instant expiresAt, Object companyId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        JwtBuilder builder = Jwts.builder()
                .subject("manager01")
                .claim("roles", List.of("WAREHOUSE_MANAGER", "SALES"))
                .claim("warehouseIds", List.of(1L, 2L));
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        return builder
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static MockServerWebExchange exchange(String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path)
                .header("X-User-Name", "ke-gia-mao")
                .header("X-User-Roles", "ADMIN")
                .header("X-User-Warehouse-Ids", "99")
                .header("X-Company-Id", "999")
                .header("X-Plan", "PRO");
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
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-User-Warehouse-Ids")).isFalse();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Company-Id")).isFalse();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Plan")).isFalse();
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
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Warehouse-Ids"))
                .isEqualTo("1,2");
    }

    @Test
    void forwardsCompanyFromTokenAndIgnoresCompanySentByClient() {
        // Client tự gửi X-Company-Id: 999 (xem exchange()), token thuộc doanh nghiệp 7: service phải nhận 7
        String valid = token(SECRET, Instant.now().plusSeconds(600), 7L);
        MockServerWebExchange exchange = exchange("/api/inventory/warehouses", "Bearer " + valid);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().get("X-Company-Id")).containsExactly("7");
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Plan")).isFalse();
    }

    @Test
    void twoCompaniesGetTheirOwnCompanyHeader() {
        for (long companyId : List.of(1L, 2L)) {
            forwarded.set(null);
            String valid = token(SECRET, Instant.now().plusSeconds(600), companyId);
            filter(true).filter(exchange("/api/inventory/warehouses", "Bearer " + valid), chain).block();
            assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Company-Id"))
                    .isEqualTo(String.valueOf(companyId));
        }
    }

    @Test
    void acceptsCompanyClaimAsIntegerOrNumericString() {
        for (Object claim : List.of(7, "7", " 7 ")) {
            forwarded.set(null);
            String valid = token(SECRET, Instant.now().plusSeconds(600), claim);
            filter(true).filter(exchange("/api/products", "Bearer " + valid), chain).block();
            assertThat(forwarded.get()).as("claim %s", claim).isNotNull();
            assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Company-Id")).isEqualTo("7");
        }
    }

    @Test
    void rejectsValidTokenWithoutCompanyWhenCompanyIsRequired() {
        for (Object claim : Arrays.asList(null, 0, -3, "abc", "")) {
            forwarded.set(null);
            String old = token(SECRET, Instant.now().plusSeconds(600), claim);
            MockServerWebExchange exchange = exchange("/api/products", "Bearer " + old);

            filter(true).filter(exchange, chain).block();

            assertThat(forwarded.get()).as("claim %s", claim).isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void forwardsTokenWithoutCompanyWhenCompanyIsNotRequired() {
        String old = token(SECRET, Instant.now().plusSeconds(600), null);
        MockServerWebExchange exchange = exchange("/api/products", "Bearer " + old);

        filter(true, false).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("manager01");
        // Không có doanh nghiệp trong token thì cũng không được chuyển tiếp giá trị client tự gửi
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Company-Id")).isFalse();
    }

    @Test
    void publicPathsStripCompanyHeaderSentByClient() {
        MockServerWebExchange exchange = exchange("/api/auth/login", null);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Company-Id")).isFalse();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Plan")).isFalse();
    }

    @Test
    void publicPathsSkipValidation() {
        MockServerWebExchange exchange = exchange("/api/auth/login", null);

        filter(true).filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void webSocketHandshakePathsSkipValidation() {
        for (String path : List.of("/ws-wms", "/ws-wms/info", "/ws-wms/123/abcdef/websocket")) {
            forwarded.set(null);
            MockServerWebExchange exchange = exchange(path, null);

            filter(true).filter(exchange, chain).block();

            assertThat(forwarded.get()).as(path).isNotNull();
        }
    }

    @Test
    void enabledFilterRefusesTooShortSecret() {
        GatewayJwtProperties weak = new GatewayJwtProperties(true, "change_me", PUBLIC_PATHS, true);

        assertThatThrownBy(() -> new JwtAuthenticationGlobalFilter(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
