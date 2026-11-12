package vn.wms.notification.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Kiểm tra JWT ở frame CONNECT, giới hạn SEND và quyền SUBSCRIBE kênh theo doanh nghiệp (WMS-22.4c): chỉ được vào
 * {@code /topic/company/{companyId}/**} của doanh nghiệp trong claim {@code companyId} đã kiểm tra chữ ký.
 */
class StompAuthChannelInterceptorTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-at-least-32-bytes";

    private static final long COMPANY_A = 1L;
    private static final long COMPANY_B = 2L;

    private final MessageChannel channel = mock(MessageChannel.class);

    private static StompAuthChannelInterceptor interceptor(boolean authRequired) {
        return new StompAuthChannelInterceptor(
                new WebSocketProperties(authRequired, SECRET, List.of("http://localhost:5173")));
    }

    private static String token(String secret, Instant expiresAt) {
        return token(secret, expiresAt, COMPANY_A);
    }

    private static String token(String secret, Instant expiresAt, Long companyId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .subject("manager01")
                .claim("roles", List.of("WAREHOUSE_MANAGER"));
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        return builder
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static Message<byte[]> frame(StompCommand command, String authorization, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Principal userOf(Message<?> message) {
        return StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).getUser();
    }

    @Test
    void whenDisabledConnectWithoutTokenPasses() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, null, null);

        Message<?> result = interceptor(false).preSend(connect, channel);

        assertThat(result).isSameAs(connect);
        assertThat(userOf(result)).isNull();
    }

    @Test
    void whenEnabledRejectsConnectWithoutToken() {
        assertThatThrownBy(() -> interceptor(true).preSend(frame(StompCommand.CONNECT, null, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Thiếu token");
        assertThatThrownBy(() -> interceptor(true).preSend(frame(StompCommand.CONNECT, "Basic abc", null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void whenEnabledRejectsTokenSignedWithAnotherKey() {
        String forged = token(OTHER_SECRET, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> interceptor(true).preSend(
                frame(StompCommand.CONNECT, "Bearer " + forged, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Token không hợp lệ");
    }

    @Test
    void whenEnabledRejectsExpiredToken() {
        String expired = token(SECRET, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> interceptor(true).preSend(
                frame(StompCommand.CONNECT, "Bearer " + expired, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    void whenEnabledRejectsGarbageToken() {
        assertThatThrownBy(() -> interceptor(true).preSend(
                frame(StompCommand.CONNECT, "Bearer khong-phai-jwt", null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void whenEnabledValidTokenAttachesUsernamePrincipalWithRoles() {
        String valid = token(SECRET, Instant.now().plusSeconds(600));

        Message<?> result = interceptor(true).preSend(frame(StompCommand.CONNECT, "Bearer " + valid, null, null), channel);

        Principal user = userOf(result);
        assertThat(user).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(user.getName()).isEqualTo("manager01");
        assertThat(((UsernamePasswordAuthenticationToken) user).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("WAREHOUSE_MANAGER");
        assertThat(StompAuthChannelInterceptor.companyIdOf(user)).isEqualTo(COMPANY_A);
    }

    @Test
    void whenEnabledTokenWithoutCompanyIsRejected() {
        String legacy = token(SECRET, Instant.now().plusSeconds(600), null);

        assertThatThrownBy(() -> interceptor(true).preSend(
                frame(StompCommand.CONNECT, "Bearer " + legacy, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("companyId");
    }

    @Test
    void stompCommandIsTreatedLikeConnect() {
        assertThatThrownBy(() -> interceptor(true).preSend(frame(StompCommand.STOMP, null, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribeIsLimitedToCompanyTopics() {
        StompAuthChannelInterceptor open = interceptor(false);
        Principal userOfA = userOfCompany(COMPANY_A);

        for (String channelName : List.of("alerts", "orders", "inventory", "kpi")) {
            assertThat(open.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/company/1/" + channelName, userOfA),
                    channel)).isNotNull();
        }
        // Topic dùng chung cũ, đích ngoài /topic, kênh lạ và đường dẫn lách đều bị từ chối
        for (String destination : List.of("/topic/alerts", "/queue/secret", "/user/topic/alerts",
                "/topic/company/1/secret", "/topic/company/1/alerts/extra", "/topic/company/abc/alerts",
                "/topic/company//alerts", "/topic/company/1/../2/alerts")) {
            assertThatThrownBy(() -> open.preSend(frame(StompCommand.SUBSCRIBE, null, destination, userOfA), channel))
                    .as(destination)
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Test
    void subscribeToOwnCompanyIsAllowedAndOtherCompanyIsRejected() {
        StompAuthChannelInterceptor secured = interceptor(true);
        Message<?> connected = secured.preSend(frame(StompCommand.CONNECT,
                "Bearer " + token(SECRET, Instant.now().plusSeconds(600), COMPANY_B), null, null), channel);
        Principal userOfB = userOf(connected);

        assertThat(secured.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/company/2/orders", userOfB), channel))
                .isNotNull();
        assertThatThrownBy(() -> secured.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/company/1/orders", userOfB), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("doanh nghiệp khác");
    }

    @Test
    void unauthenticatedSubscribeIsRejected() {
        // Bật xác thực: phiên chưa CONNECT thành công
        assertThatThrownBy(() -> interceptor(true).preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/company/1/alerts", null), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("chưa xác thực");
        // Tắt xác thực nhưng không có doanh nghiệp mặc định: cũng không được vào kênh nào
        assertThatThrownBy(() -> interceptor(false).preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/company/1/alerts", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        // Principal không mang doanh nghiệp (không do interceptor gắn) cũng bị từ chối
        Principal withoutCompany = UsernamePasswordAuthenticationToken.authenticated("manager01", null, List.of());
        assertThatThrownBy(() -> interceptor(true).preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/company/1/alerts", withoutCompany), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("chưa xác định doanh nghiệp");
    }

    @Test
    void whenDisabledSessionWithoutTokenUsesDevDefaultCompanyOnly() {
        StompAuthChannelInterceptor dev = new StompAuthChannelInterceptor(
                new WebSocketProperties(false, SECRET, List.of("http://localhost:5173")), COMPANY_A);

        Principal anonymous = userOf(dev.preSend(frame(StompCommand.CONNECT, null, null, null), channel));
        assertThat(StompAuthChannelInterceptor.companyIdOf(anonymous)).isEqualTo(COMPANY_A);
        assertThat(dev.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/company/1/kpi", anonymous), channel))
                .isNotNull();
        assertThatThrownBy(() -> dev.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/company/2/kpi", anonymous),
                channel)).isInstanceOf(MessageDeliveryException.class);

        // Có token hợp lệ thì doanh nghiệp lấy từ token, không lấy doanh nghiệp mặc định
        Principal userOfB = userOf(dev.preSend(frame(StompCommand.CONNECT,
                "Bearer " + token(SECRET, Instant.now().plusSeconds(600), COMPANY_B), null, null), channel));
        assertThat(StompAuthChannelInterceptor.companyIdOf(userOfB)).isEqualTo(COMPANY_B);
    }

    @Test
    void sendFromClientIsAlwaysRejected() {
        assertThatThrownBy(() -> interceptor(false).preSend(
                frame(StompCommand.SEND, null, "/topic/company/1/alerts", userOfCompany(COMPANY_A)), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private static Principal userOfCompany(long companyId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new StompPrincipal("manager01", companyId), null, List.of());
    }

    @Test
    void enabledInterceptorRefusesTooShortSecret() {
        WebSocketProperties weak = new WebSocketProperties(true, "change_me", List.of());

        assertThatThrownBy(() -> new StompAuthChannelInterceptor(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
