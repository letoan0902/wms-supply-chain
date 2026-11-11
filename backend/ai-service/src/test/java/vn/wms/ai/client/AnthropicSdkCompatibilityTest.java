package vn.wms.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.Timeout;
import com.anthropic.models.beta.messages.BetaMessage;
import org.junit.jupiter.api.Test;
import vn.wms.ai.AiTestSupport;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SDK của Anthropic được biên dịch với Jackson 2.19 nhưng Spring Boot 3.3 ép Jackson 2.17. Hai test dưới đây chạy
 * đúng đường code của chế độ LIVE (dựng client, đọc phản hồi JSON) mà không gọi mạng, để phát hiện sớm lỗi không
 * tương thích thư viện. Khóa dùng ở đây là chuỗi giả, client không gửi request nào.
 */
class AnthropicSdkCompatibilityTest {

    @Test
    void clientCanBeBuiltWithTimeoutAndRetriesUnderSpringBootJackson() {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey("khoa-gia-chi-dung-trong-test")
                .timeout(Timeout.builder().connect(Duration.ofSeconds(10)).request(Duration.ofSeconds(60)).build())
                .maxRetries(1)
                .build();
        try {
            assertThat(client.beta().messages()).isNotNull();
        } finally {
            client.close();
        }
    }

    @Test
    void responseJsonIsParsedAndMapped() throws Exception {
        String json = """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":[{"type":"text","text":"Bước 1: vào mục Kho"}],
                 "stop_reason":"max_tokens","stop_sequence":null,
                 "usage":{"input_tokens":12,"output_tokens":34}}
                """;
        BetaMessage message = ObjectMappers.jsonMapper().readValue(json, BetaMessage.class);

        AiReply reply = new AnthropicAiClient(null, AiTestSupport.properties(false, true)).toReply(message);

        assertThat(reply).isEqualTo(AiReply.answered("Bước 1: vào mục Kho", "max_tokens", 12, 34, true));
    }
}
