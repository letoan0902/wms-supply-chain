package vn.wms.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AnthropicAiClient;
import vn.wms.ai.client.MockAiClient;

import java.time.Duration;

/**
 * Chọn cài đặt {@link AiClient} lúc khởi động:
 * <ul>
 *   <li>{@code app.ai.mock=true} (mặc định): {@link MockAiClient};</li>
 *   <li>{@code app.ai.mock=false} nhưng biến môi trường {@value #API_KEY_ENV} rỗng: vẫn dùng {@link MockAiClient}
 *       và ghi đúng một dòng cảnh báo, để service không chết chỉ vì thiếu khóa;</li>
 *   <li>còn lại: {@link AnthropicAiClient} với client của SDK tự đọc khóa từ biến môi trường.</li>
 * </ul>
 * Khóa chỉ được kiểm tra có hay không, không bao giờ được ghi log, trả về hay lưu ở nơi khác.
 */
@Configuration(proxyBeanMethods = false)
public class AiClientConfig {

    /** Biến môi trường chứa khóa API của Anthropic, chỉ đặt trong file .env trên máy chủ. */
    public static final String API_KEY_ENV = "ANTHROPIC_API_KEY";

    /** Thời gian chờ mở kết nối tới máy chủ của Anthropic. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(AiClientConfig.class);

    @Bean
    AiRuntime aiRuntime(AiProperties properties) {
        AiMode mode = selectMode(properties, System.getenv(API_KEY_ENV));
        if (mode == AiMode.LIVE) {
            log.info("ai-service gọi Claude thật: mô hình {}, effort {}, max_tokens {}, timeout {} giây, fallbacks {}",
                    properties.model(), properties.effort(), properties.maxTokens(),
                    properties.timeout().toSeconds(), properties.serverFallback() ? "default" : "tắt");
        } else if (properties.mock()) {
            log.info("ai-service chạy chế độ giả lập (AI_MOCK=true): trả câu trả lời mẫu, không gọi Claude");
        } else {
            log.warn("AI_MOCK=false nhưng biến môi trường {} chưa có giá trị: ai-service chuyển sang chế độ giả lập",
                    API_KEY_ENV);
        }
        return new AiRuntime(mode, properties.model());
    }

    @Bean
    AiClient aiClient(AiRuntime runtime, AiProperties properties) {
        if (runtime.isMock()) {
            return new MockAiClient();
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(Timeout.builder()
                        .connect(CONNECT_TIMEOUT)
                        .request(properties.timeout())
                        .build())
                .maxRetries(properties.maxRetries())
                .build();
        return new AnthropicAiClient(client, properties);
    }

    /** Chế độ chạy: chỉ gọi Claude thật khi đã tắt giả lập VÀ có khóa. */
    static AiMode selectMode(AiProperties properties, String apiKey) {
        if (properties.mock() || apiKey == null || apiKey.isBlank()) {
            return AiMode.MOCK;
        }
        return AiMode.LIVE;
    }
}
