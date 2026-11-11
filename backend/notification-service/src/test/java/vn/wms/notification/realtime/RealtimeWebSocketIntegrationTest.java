package vn.wms.notification.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.LowStockAlertEvent;
import vn.wms.notification.messaging.AlertEventsListener;
import vn.wms.notification.service.EmailService;

import javax.crypto.SecretKey;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Nối thật vào hub /ws-wms (Tomcat cổng ngẫu nhiên, xác thực JWT bật), SUBSCRIBE kênh của doanh nghiệp
 * ({@code /topic/company/{companyId}/alerts}, {@code /kpi}) rồi gọi thẳng listener của notification.alerts như khi
 * RabbitMQ giao tin. Kiểm tra cả tách kênh theo doanh nghiệp (WMS-22.4c): tin của A không tới phiên của B, người dùng
 * B đăng ký kênh của A bị từ chối. Listener RabbitMQ không tự chạy (profile test), Redis được mock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.websocket.auth-required=true",
        "app.websocket.jwt-secret=" + RealtimeWebSocketIntegrationTest.SECRET})
@ActiveProfiles("test")
class RealtimeWebSocketIntegrationTest {

    static final String SECRET = "integration-test-secret-key-at-least-32-bytes";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long COMPANY_A = 1L;
    private static final long COMPANY_B = 2L;

    @LocalServerPort
    private int port;

    @Autowired
    private AlertEventsListener alertEventsListener;

    @Autowired
    private SimpUserRegistry userRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private StringRedisTemplate redis;

    @MockBean
    private EmailService emailService;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private WebSocketStompClient stompClient;
    private StompSession session;
    private StompSession otherSession;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(hashOps.entries(anyString())).thenReturn(Map.of("lowStockAlerts", "1", "updatedAt", "2026-11-05T08:00:00Z"));
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (otherSession != null && otherSession.isConnected()) {
            otherSession.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void subscriberReceivesLowStockEnvelopeAndKpiSnapshotOverRawWebSocket() throws Exception {
        session = connect(new StandardWebSocketClient(), "ws://localhost:" + port + "/ws-wms", validToken());
        BlockingQueue<JsonNode> alerts = subscribe(session, "/topic/company/1/alerts");
        BlockingQueue<JsonNode> kpi = subscribe(session, "/topic/company/1/kpi");
        awaitSubscriptions(2);

        LowStockAlertEvent event = new LowStockAlertEvent(5L, "Kho Hà Nội", 100L, "SKU-001", "Thùng carton",
                new BigDecimal("5"), new BigDecimal("20"), Instant.parse("2026-11-05T07:59:00Z"));
        alertEventsListener.onMessage(rabbitMessage(event, WmsEvents.RoutingKeys.ALERT_LOW_STOCK, "msg-low-1"));

        JsonNode alert = alerts.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(alert).as("tin trên /topic/company/1/alerts").isNotNull();
        assertThat(alert.path("id").asText()).isEqualTo("msg-low-1");
        assertThat(alert.path("type").asText()).isEqualTo("LOW_STOCK");
        assertThat(alert.path("severity").asText()).isEqualTo("warning");
        assertThat(alert.path("title").asText()).isEqualTo("Cảnh báo tồn kho thấp");
        assertThat(alert.path("message").asText()).isEqualTo("SKU-001 Thùng carton tại Kho Hà Nội còn 5, dưới ngưỡng 20");
        assertThat(alert.path("occurredAt").asText()).isEqualTo("2026-11-05T07:59:00Z");
        assertThat(alert.path("payload").path("skuCode").asText()).isEqualTo("SKU-001");
        assertThat(alert.path("payload").path("minimumQuantity").decimalValue()).isEqualByComparingTo("20");

        JsonNode snapshot = kpi.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(snapshot).as("tin trên /topic/company/1/kpi").isNotNull();
        assertThat(snapshot.path("type").asText()).isEqualTo("KPI_UPDATED");
        assertThat(snapshot.path("severity").asText()).isEqualTo("info");
        assertThat(snapshot.path("payload").path("lowStockAlerts").asLong()).isEqualTo(1);
        assertThat(snapshot.path("payload").path("updatedAt").asText()).isEqualTo("2026-11-05T08:00:00Z");
    }

    @Test
    void eventOfCompanyAReachesOnlyCompanyAAndDoesNotTouchKpiOfCompanyB() throws Exception {
        session = connect(new StandardWebSocketClient(), "ws://localhost:" + port + "/ws-wms", validToken());
        otherSession = connect(new StandardWebSocketClient(), "ws://localhost:" + port + "/ws-wms",
                tokenOf("manager_b", COMPANY_B));
        BlockingQueue<JsonNode> alertsOfA = subscribe(session, "/topic/company/1/alerts");
        BlockingQueue<JsonNode> alertsOfB = subscribe(otherSession, "/topic/company/2/alerts");
        BlockingQueue<JsonNode> kpiOfB = subscribe(otherSession, "/topic/company/2/kpi");
        awaitSubscribed("manager01", "/topic/company/1/alerts");
        awaitSubscribed("manager_b", "/topic/company/2/alerts");
        awaitSubscribed("manager_b", "/topic/company/2/kpi");

        LowStockAlertEvent event = new LowStockAlertEvent(5L, "Kho Hà Nội", 100L, "SKU-001", "Thùng carton",
                new BigDecimal("5"), new BigDecimal("20"), Instant.parse("2026-11-05T07:59:00Z"));
        alertEventsListener.onMessage(rabbitMessage(event, WmsEvents.RoutingKeys.ALERT_LOW_STOCK, "msg-low-a"));

        assertThat(alertsOfA.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as("A nhận cảnh báo của A").isNotNull();
        assertThat(alertsOfB.poll(1, TimeUnit.SECONDS)).as("B không nhận cảnh báo của A").isNull();
        assertThat(kpiOfB.poll(1, TimeUnit.SECONDS)).as("B không nhận KPI của A").isNull();
        // Bộ đếm Redis chỉ tăng ở khóa của A
        verify(hashOps).increment(startsWith("wms:notification:kpi:1:"), eq("lowStockAlerts"), eq(1L));
        verify(hashOps, never()).increment(startsWith("wms:notification:kpi:2:"), any(), anyLong());
    }

    @Test
    void userOfCompanyBSubscribingChannelOfCompanyAIsRejected() throws Exception {
        stompClient = newStompClient(new StandardWebSocketClient());
        CompletableFuture<String> error = new CompletableFuture<>();
        otherSession = stompClient.connectAsync("ws://localhost:" + port + "/ws-wms", new WebSocketHttpHeaders(),
                connectHeaders(tokenOf("manager_b", COMPANY_B)), new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        error.complete(headers.getFirst("message"));
                    }
                }).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        otherSession.subscribe("/topic/company/1/alerts", new StompSessionHandlerAdapter() { });

        assertThat(error.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).contains("doanh nghiệp khác");
        assertThat(userRegistry.findSubscriptions(subscription -> "manager_b".equals(subscription.getSession().getUser().getName())
                && subscription.getDestination().startsWith("/topic/company/1/"))).isEmpty();
    }

    @Test
    void kpiEndpointRequiresCompany() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/notifications/realtime/kpi", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asText()).isEqualTo("COMPANY_REQUIRED");
    }

    @Test
    void kpiEndpointReturnsCurrentSnapshotInApiResponse() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Company-Id", String.valueOf(COMPANY_A));
        JsonNode body = objectMapper.readTree(restTemplate.exchange("/api/notifications/realtime/kpi", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody());

        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("lowStockAlerts").asLong()).isEqualTo(1);
        assertThat(body.path("data").path("purchaseOrders").asLong()).isZero();
        assertThat(body.path("data").path("date").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(body.path("data").path("updatedAt").asText()).isEqualTo("2026-11-05T08:00:00Z");
        verify(hashOps).entries(startsWith("wms:notification:kpi:1:"));
    }

    @Test
    void sockJsClientCanConnectWithTokenInConnectFrame() throws Exception {
        SockJsClient sockJsClient = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));

        session = connect(sockJsClient, "http://localhost:" + port + "/ws-wms", validToken());

        assertThat(session.isConnected()).isTrue();
    }

    @Test
    void connectWithoutTokenIsRejectedWithErrorFrame() throws Exception {
        stompClient = newStompClient(new StandardWebSocketClient());
        CompletableFuture<String> error = new CompletableFuture<>();

        stompClient.connectAsync("ws://localhost:" + port + "/ws-wms", new WebSocketHttpHeaders(), new StompHeaders(),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        error.complete(headers.getFirst("message"));
                    }
                });

        assertThat(error.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).contains("Thiếu token");
    }

    @Test
    void handshakeFromForeignOriginIsRefused() {
        stompClient = newStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://evil.example");

        assertThatThrownBy(() -> stompClient.connectAsync("ws://localhost:" + port + "/ws-wms", handshakeHeaders,
                connectHeaders(validToken()), new StompSessionHandlerAdapter() { })
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    private StompSession connect(WebSocketClient client, String url, String token) throws Exception {
        if (stompClient == null) {
            stompClient = newStompClient(client);
        }
        return stompClient.connectAsync(url, new WebSocketHttpHeaders(), connectHeaders(token),
                new StompSessionHandlerAdapter() { }).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private static WebSocketStompClient newStompClient(WebSocketClient client) {
        WebSocketStompClient stompClient = new WebSocketStompClient(client);
        // Server gửi content-type application/json; đọc thô thành JsonNode để kiểm tra đúng JSON gửi cho frontend
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        // Client test không gửi nhịp tim nên không cần TaskScheduler
        stompClient.setDefaultHeartbeat(new long[]{0, 0});
        return stompClient;
    }

    private static StompHeaders connectHeaders(String token) {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return headers;
    }

    private static BlockingQueue<JsonNode> subscribe(StompSession session, String destination) {
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((JsonNode) payload);
            }
        });
        return received;
    }

    /** SUBSCRIBE được server xử lý bất đồng bộ: chờ tới khi registry ghi nhận đủ đăng ký của người dùng đã xác thực. */
    private void awaitSubscriptions(int expected) {
        await().atMost(TIMEOUT).until(() -> userRegistry.findSubscriptions(
                subscription -> subscription.getDestination().startsWith("/topic/")).size() >= expected);
    }

    /** Chờ tới khi registry ghi nhận một đăng ký cụ thể của một người dùng. */
    private void awaitSubscribed(String username, String destination) {
        await().atMost(TIMEOUT).until(() -> !userRegistry.findSubscriptions(subscription ->
                username.equals(subscription.getSession().getUser().getName())
                        && destination.equals(subscription.getDestination())).isEmpty());
    }

    private Message rabbitMessage(Object payload, String routingKey, String messageId) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setReceivedRoutingKey(routingKey);
        properties.setMessageId(messageId);
        properties.setHeader(WmsEventPublisher.HEADER_COMPANY_ID, COMPANY_A);
        return new Message(objectMapper.writeValueAsBytes(payload), properties);
    }

    private static String validToken() {
        return tokenOf("manager01", COMPANY_A);
    }

    private static String tokenOf(String username, long companyId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(username)
                .claim("roles", List.of("WAREHOUSE_MANAGER"))
                .claim("companyId", companyId)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
