package vn.wms.notification.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.time.Clock;

/**
 * Hub WebSocket STOMP của hệ thống (WMS-02.3).
 *
 * <ul>
 *   <li>Endpoint {@value #ENDPOINT}: bản SockJS (trình duyệt dùng {@code sockjs-client}, đường dẫn con
 *       {@code /ws-wms/info}, {@code /ws-wms/{server}/{session}/...}) và bản WebSocket thuần cùng đường dẫn
 *       ({@code ws://.../ws-wms}) cho client không dùng SockJS.</li>
 *   <li>Simple broker trong bộ nhớ với tiền tố {@code /topic}, nhịp tim 10 giây hai chiều.</li>
 *   <li>Không đặt application destination prefix và không có {@code @MessageMapping}: client chỉ SUBSCRIBE,
 *       frame SEND bị {@link StompAuthChannelInterceptor} từ chối.</li>
 * </ul>
 *
 * <p>Danh sách topic và phong bì tin nhắn: docs/MESSAGING.md mục 8.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Đường dẫn endpoint STOMP, gateway định tuyến {@code /ws-wms/**} tới service này. */
    public static final String ENDPOINT = "/ws-wms";
    private static final String BROKER_PREFIX = "/topic";
    private static final long HEARTBEAT_MILLIS = 10_000;

    private final WebSocketProperties properties;
    private final TaskScheduler messageBrokerTaskScheduler;

    /**
     * Dùng lại bộ lập lịch {@code messageBrokerTaskScheduler} do Spring tạo cho hạ tầng STOMP để gửi nhịp tim;
     * {@code @Lazy} vì bean này được tạo sau lớp cấu hình (cách làm theo tài liệu Spring Framework).
     */
    public WebSocketConfig(WebSocketProperties properties,
                           @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler) {
        this.properties = properties;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = properties.allowedOriginPatterns();
        // Bản WebSocket thuần ánh xạ đúng /ws-wms, bản SockJS ánh xạ /ws-wms/** nên hai bản không xung đột.
        registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(origins);
        registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(origins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(BROKER_PREFIX)
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(messageBrokerTaskScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor());
    }

    @Bean
    StompAuthChannelInterceptor stompAuthChannelInterceptor() {
        return new StompAuthChannelInterceptor(properties);
    }

    /** Đồng hồ hệ thống (UTC) cho thời điểm trong phong bì và bộ đếm KPI theo ngày; test thay bằng đồng hồ cố định. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
