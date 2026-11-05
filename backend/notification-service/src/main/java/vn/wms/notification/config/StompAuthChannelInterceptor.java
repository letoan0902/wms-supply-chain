package vn.wms.notification.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Chặn frame STOMP từ client trên kênh inbound của hub {@code /ws-wms}.
 *
 * <ul>
 *   <li>CONNECT: khi {@code app.websocket.auth-required=true}, đọc header {@code Authorization: Bearer <token>} trong
 *       frame (trình duyệt không gắn được header vào handshake), kiểm tra chữ ký HS256 và hạn giống
 *       {@code JwtAuthenticationGlobalFilter} của api-gateway rồi gắn {@code Principal} là username. Thiếu hoặc sai token
 *       thì ném {@link MessageDeliveryException}, Spring trả frame ERROR và đóng kết nối. Khi tắt thì cho qua.</li>
 *   <li>SUBSCRIBE: chỉ được đăng ký đích {@code /topic/**}; khi bật xác thực thì phiên phải đã CONNECT thành công.</li>
 *   <li>SEND: luôn bị từ chối. Hub chỉ đẩy tin một chiều từ server, client không được phát tin lên các topic.</li>
 * </ul>
 */
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String TOPIC_PREFIX = "/topic/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final boolean authRequired;
    private final SecretKey signingKey;

    public StompAuthChannelInterceptor(WebSocketProperties properties) {
        this.authRequired = properties.authRequired();
        if (authRequired) {
            byte[] keyBytes = properties.jwtSecret() == null
                    ? new byte[0] : properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "app.websocket.jwt-secret (JWT_SECRET) phải dài tối thiểu 32 byte (256 bit) cho thuật toán HS256");
            }
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } else {
            this.signingKey = null;
            log.warn("Xác thực JWT cho WebSocket /ws-wms đang TẮT (app.websocket.auth-required=false) "
                    + "- chỉ dùng cho giai đoạn phát triển");
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            // Nhịp tim (heartbeat) và tin nội bộ không có lệnh STOMP
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> authenticate(message, accessor);
            case SUBSCRIBE -> checkSubscription(message, accessor);
            case SEND -> throw new MessageDeliveryException(message,
                    "Hub thông báo chỉ đẩy tin từ server, client không được gửi tin (SEND)");
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK, NACK: cho qua
            }
        }
        return message;
    }

    private void authenticate(Message<?> message, StompHeaderAccessor accessor) {
        if (!authRequired) {
            return;
        }
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException(message,
                    "Thiếu token xác thực (header Authorization dạng Bearer trong frame CONNECT)");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Từ chối CONNECT với token không hợp lệ: {}", ex.getMessage());
            throw new MessageDeliveryException(message, "Token không hợp lệ hoặc đã hết hạn");
        }
        String username = claims.getSubject();
        if (!StringUtils.hasText(username)) {
            throw new MessageDeliveryException(message, "Token không có thông tin người dùng (subject)");
        }
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(username, null, authoritiesOf(claims)));
        log.debug("Phiên WebSocket {} đã xác thực cho {}", accessor.getSessionId(), username);
    }

    private void checkSubscription(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            throw new MessageDeliveryException(message,
                    "Chỉ được đăng ký các đích /topic/**, không hỗ trợ: " + destination);
        }
        if (authRequired && accessor.getUser() == null) {
            throw new MessageDeliveryException(message, "Phiên WebSocket chưa xác thực, cần CONNECT kèm token hợp lệ");
        }
    }

    private static List<GrantedAuthority> authoritiesOf(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
