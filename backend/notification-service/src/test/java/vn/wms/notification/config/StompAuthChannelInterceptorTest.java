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

/** Kiểm tra JWT ở frame CONNECT và giới hạn SUBSCRIBE, SEND của hub WebSocket. */
class StompAuthChannelInterceptorTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "another-secret-key-that-is-also-at-least-32-bytes";

    private final MessageChannel channel = mock(MessageChannel.class);

    private static StompAuthChannelInterceptor interceptor(boolean authRequired) {
        return new StompAuthChannelInterceptor(
                new WebSocketProperties(authRequired, SECRET, List.of("http://localhost:5173")));
    }

    private static String token(String secret, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("manager01")
                .claim("roles", List.of("WAREHOUSE_MANAGER"))
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
    }

    @Test
    void stompCommandIsTreatedLikeConnect() {
        assertThatThrownBy(() -> interceptor(true).preSend(frame(StompCommand.STOMP, null, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribeIsLimitedToTopics() {
        StompAuthChannelInterceptor open = interceptor(false);

        assertThat(open.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/alerts", null), channel)).isNotNull();
        assertThatThrownBy(() -> open.preSend(frame(StompCommand.SUBSCRIBE, null, "/queue/secret", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> open.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/topic/alerts", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void whenEnabledSubscribeRequiresAuthenticatedSession() {
        StompAuthChannelInterceptor secured = interceptor(true);
        Principal user = UsernamePasswordAuthenticationToken.authenticated("manager01", null, List.of());

        assertThatThrownBy(() -> secured.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/alerts", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(secured.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/alerts", user), channel)).isNotNull();
    }

    @Test
    void sendFromClientIsAlwaysRejected() {
        assertThatThrownBy(() -> interceptor(false).preSend(frame(StompCommand.SEND, null, "/topic/alerts", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void enabledInterceptorRefusesTooShortSecret() {
        WebSocketProperties weak = new WebSocketProperties(true, "change_me", List.of());

        assertThatThrownBy(() -> new StompAuthChannelInterceptor(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
