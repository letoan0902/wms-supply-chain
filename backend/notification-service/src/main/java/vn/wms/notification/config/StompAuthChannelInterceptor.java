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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;
import vn.wms.notification.realtime.RealtimeEventType;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chặn frame STOMP từ client trên kênh inbound của hub {@code /ws-wms}.
 *
 * <ul>
 *   <li>CONNECT: đọc header {@code Authorization: Bearer <token>} trong frame (trình duyệt không gắn được header vào
 *       handshake), kiểm tra chữ ký HS256 và hạn giống {@code JwtAuthenticationGlobalFilter} của api-gateway rồi gắn
 *       {@code Principal} là {@link StompPrincipal} (username và claim {@code companyId}). Khi
 *       {@code app.websocket.auth-required=true}: thiếu hoặc sai token, hoặc token không xác định được doanh nghiệp,
 *       thì ném {@link MessageDeliveryException}, Spring trả frame ERROR và đóng kết nối. Khi tắt (máy phát triển):
 *       token hợp lệ vẫn được dùng, không có token thì phiên nhận doanh nghiệp mặc định
 *       ({@code app.tenant.default-company-id}) nếu có cấu hình.</li>
 *   <li>SUBSCRIBE (WMS-22.4c): chỉ được đăng ký {@code /topic/company/{companyId}/alerts|orders|inventory|kpi} với
 *       {@code companyId} đúng bằng doanh nghiệp của phiên. Đích khác, phiên chưa xác thực hoặc kênh của doanh nghiệp
 *       khác đều bị từ chối.</li>
 *   <li>SEND: luôn bị từ chối. Hub chỉ đẩy tin một chiều từ server, client không được phát tin lên các topic.</li>
 * </ul>
 */
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String CLAIM_COMPANY_ID = "companyId";
    /** Tên của phiên không có token khi tắt xác thực (chỉ ở máy phát triển). */
    static final String DEV_ANONYMOUS_NAME = "dev-anonymous";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;
    /** {@code /topic/company/{companyId}/{kênh}}; kênh được kiểm tra riêng với {@link RealtimeEventType#CHANNELS}. */
    private static final Pattern COMPANY_TOPIC = Pattern.compile(
            "^" + Pattern.quote(RealtimeEventType.COMPANY_TOPIC_PREFIX) + "(\\d{1,18})/([a-z]+)$");

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final boolean authRequired;
    private final SecretKey signingKey;
    private final Long defaultCompanyId;

    public StompAuthChannelInterceptor(WebSocketProperties properties) {
        this(properties, null);
    }

    /**
     * @param defaultCompanyId doanh nghiệp mặc định của máy phát triển ({@code app.tenant.default-company-id}), chỉ
     *                         dùng khi phiên không có token hợp lệ và xác thực đang tắt; máy chủ để null
     */
    public StompAuthChannelInterceptor(WebSocketProperties properties, Long defaultCompanyId) {
        this.authRequired = properties.authRequired();
        this.defaultCompanyId = defaultCompanyId != null && defaultCompanyId > 0 ? defaultCompanyId : null;
        byte[] keyBytes = properties.jwtSecret() == null
                ? new byte[0] : properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length >= MIN_SECRET_BYTES) {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } else if (authRequired) {
            throw new IllegalStateException(
                    "app.websocket.jwt-secret (JWT_SECRET) phải dài tối thiểu 32 byte (256 bit) cho thuật toán HS256");
        } else {
            this.signingKey = null;
        }
        if (!authRequired) {
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
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        boolean hasBearer = authorization != null && authorization.startsWith(BEARER_PREFIX);
        if (!hasBearer) {
            if (authRequired) {
                throw new MessageDeliveryException(message,
                        "Thiếu token xác thực (header Authorization dạng Bearer trong frame CONNECT)");
            }
            attachDevDefault(accessor);
            return;
        }
        Claims claims;
        try {
            if (signingKey == null) {
                throw new IllegalArgumentException("Chưa cấu hình khóa ký JWT đủ dài");
            }
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            if (authRequired) {
                log.debug("Từ chối CONNECT với token không hợp lệ: {}", ex.getMessage());
                throw new MessageDeliveryException(message, "Token không hợp lệ hoặc đã hết hạn");
            }
            log.debug("Bỏ qua token không hợp lệ khi tắt xác thực WebSocket: {}", ex.getMessage());
            attachDevDefault(accessor);
            return;
        }
        String username = claims.getSubject();
        if (!StringUtils.hasText(username)) {
            throw new MessageDeliveryException(message, "Token không có thông tin người dùng (subject)");
        }
        Long companyId = companyIdOf(claims);
        if (companyId == null) {
            companyId = defaultCompanyId;
        }
        if (companyId == null) {
            // Token cấp trước khi có mô hình nhiều doanh nghiệp: gateway cũng trả 401 để giao diện đăng nhập lại
            throw new MessageDeliveryException(message,
                    "Token không có thông tin doanh nghiệp (companyId), vui lòng đăng nhập lại");
        }
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                new StompPrincipal(username, companyId), null, authoritiesOf(claims)));
        log.debug("Phiên WebSocket {} đã xác thực cho {} (companyId={})", accessor.getSessionId(), username, companyId);
    }

    /** Khi tắt xác thực: phiên không có token hợp lệ nhận doanh nghiệp mặc định của máy phát triển nếu có. */
    private void attachDevDefault(StompHeaderAccessor accessor) {
        if (defaultCompanyId == null) {
            return;
        }
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                new StompPrincipal(DEV_ANONYMOUS_NAME, defaultCompanyId), null, List.of()));
    }

    private void checkSubscription(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : COMPANY_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches() || !RealtimeEventType.CHANNELS.contains(matcher.group(2))) {
            throw new MessageDeliveryException(message, "Chỉ được đăng ký các kênh "
                    + RealtimeEventType.COMPANY_TOPIC_PREFIX + "{companyId}/alerts|orders|inventory|kpi, không hỗ trợ: "
                    + destination);
        }
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessageDeliveryException(message, "Phiên WebSocket chưa xác thực, cần CONNECT kèm token hợp lệ");
        }
        Long sessionCompanyId = companyIdOf(user);
        if (sessionCompanyId == null) {
            throw new MessageDeliveryException(message,
                    "Phiên WebSocket chưa xác định doanh nghiệp, cần CONNECT kèm token có companyId");
        }
        Long requestedCompanyId = Long.valueOf(matcher.group(1));
        if (!sessionCompanyId.equals(requestedCompanyId)) {
            log.warn("Từ chối SUBSCRIBE {} của {} (doanh nghiệp {}): kênh của doanh nghiệp khác",
                    destination, user.getName(), sessionCompanyId);
            throw new MessageDeliveryException(message, "Không được đăng ký kênh của doanh nghiệp khác");
        }
    }

    /** Doanh nghiệp của phiên, gắn ở CONNECT; {@code null} nếu phiên không có {@link StompPrincipal}. */
    static Long companyIdOf(Principal user) {
        if (user instanceof StompPrincipal principal) {
            return principal.companyId();
        }
        if (user instanceof Authentication authentication
                && authentication.getPrincipal() instanceof StompPrincipal principal) {
            return principal.companyId();
        }
        return null;
    }

    /** Đọc claim companyId như api-gateway: số hoặc chuỗi số dương, sai định dạng thì coi như không có. */
    static Long companyIdOf(Claims claims) {
        Object value = claims.get(CLAIM_COMPANY_ID);
        if (value == null) {
            return null;
        }
        try {
            long id = value instanceof Number number ? number.longValue() : Long.parseLong(value.toString().trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
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
