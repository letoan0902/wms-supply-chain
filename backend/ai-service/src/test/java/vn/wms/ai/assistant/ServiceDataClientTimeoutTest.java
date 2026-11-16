package vn.wms.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.wms.ai.config.ServiceClientConfig;
import vn.wms.ai.config.ServiceClientProperties;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service phía sau treo (nhận kết nối nhưng không trả lời): {@link ServiceDataClient} phải dừng sau thời gian chờ đọc
 * của {@link ServiceClientConfig} và trả lỗi tra cứu rõ ràng, không giữ request của trợ lý tới khi nginx cắt.
 * Dùng máy chủ HTTP của JDK trên cổng ngẫu nhiên của localhost, không gọi mạng ngoài.
 */
class ServiceDataClientTimeoutTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;
    private String serviceId;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"success\":true,\"data\":{\"ok\":true}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        serviceId = "127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void hungServiceTimesOutWithLookupMessage() {
        ServiceClientProperties properties = new ServiceClientProperties(
                Duration.ofSeconds(1), Duration.ofMillis(500), Duration.ofSeconds(120));
        ServiceDataClient client = new ServiceDataClient(
                new ServiceClientConfig().loadBalancedRestClientBuilder(properties), new ObjectMapper(), properties);

        long start = System.nanoTime();
        ServiceDataClient.Result result = client.get(serviceId, "/slow", Map.of(), Map.of());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        assertThat(result.status()).isEqualTo(ServiceDataClient.Status.UNAVAILABLE);
        assertThat(result.message()).isEqualTo("Chưa tra cứu được: dịch vụ " + serviceId
                + " phản hồi quá lâu (quá 0,5 giây), vui lòng thử lại sau.");
    }

    @Test
    void defaultsAreAFewSeconds() {
        ServiceClientProperties defaults = new ServiceClientProperties(
                null, null, null);

        assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(defaults.bulkReadTimeout()).isEqualTo(Duration.ofSeconds(120));
    }
}
