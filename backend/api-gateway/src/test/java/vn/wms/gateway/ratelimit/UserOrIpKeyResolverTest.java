package vn.wms.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class UserOrIpKeyResolverTest {

    private static UserOrIpKeyResolver resolver(boolean enabled) {
        return new UserOrIpKeyResolver(new RateLimitProperties(enabled));
    }

    @Test
    void usesUserNameHeaderWhenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").header("X-User-Name", "manager01"));

        assertThat(resolver(true).resolve(exchange).block()).isEqualTo("user:manager01");
    }

    @Test
    void fallsBackToFirstForwardedForAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .remoteAddress(new InetSocketAddress("10.0.0.1", 50000)));

        assertThat(resolver(true).resolve(exchange).block()).isEqualTo("ip:203.0.113.10");
    }

    @Test
    void fallsBackToRemoteAddressWithoutProxyHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .remoteAddress(new InetSocketAddress("192.168.1.20", 50000)));

        assertThat(resolver(true).resolve(exchange).block()).isEqualTo("ip:192.168.1.20");
    }

    @Test
    void returnsUnknownWhenNoAddressAvailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));

        assertThat(resolver(true).resolve(exchange).block()).isEqualTo("ip:" + UserOrIpKeyResolver.UNKNOWN_CLIENT);
    }

    @Test
    void returnsEmptyWhenDisabledSoLimiterIsSkipped() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").header("X-User-Name", "manager01"));

        assertThat(resolver(false).resolve(exchange).blockOptional()).isEmpty();
    }
}
