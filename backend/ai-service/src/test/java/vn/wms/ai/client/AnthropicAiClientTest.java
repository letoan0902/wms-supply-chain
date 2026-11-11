package vn.wms.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import org.mockito.ArgumentCaptor;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.services.blocking.BetaService;
import com.anthropic.services.blocking.beta.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.config.AiEffort;
import vn.wms.ai.config.AiProperties;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra phần ánh xạ của {@link AnthropicAiClient} mà không gọi mạng: client của SDK là đối tượng giả lập.
 */
class AnthropicAiClientTest {

    private static final AiRequest REQUEST = new AiRequest("Bạn là trợ lý kho.", "Tạo kho thế nào?", 4096);

    private AnthropicClient sdkClient;
    private MessageService messageService;
    private AnthropicAiClient client;

    @BeforeEach
    void setUp() {
        sdkClient = mock(AnthropicClient.class);
        BetaService betaService = mock(BetaService.class);
        messageService = mock(MessageService.class);
        when(sdkClient.beta()).thenReturn(betaService);
        when(betaService.messages()).thenReturn(messageService);
        client = new AnthropicAiClient(sdkClient, AiTestSupport.properties(false, true));
    }

    @Test
    void buildParams_usesConfiguredModelTokensEffortAndCachedSystemPromptWithoutSamplingParameters() {
        MessageCreateParams params = client.buildParams(REQUEST);
        JsonNode body = ObjectMappers.jsonMapper().valueToTree(params._body());

        assertThat(body.path("model").asText()).isEqualTo("claude-sonnet-5-5");
        assertThat(body.path("max_tokens").asLong()).isEqualTo(4096);
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("system").get(0).path("text").asText()).isEqualTo("Bạn là trợ lý kho.");
        assertThat(body.path("system").get(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(params.betas()).hasValue(List.of(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01));
        for (String forbidden : List.of("temperature", "top_p", "top_k", "thinking", "budget_tokens")) {
            assertThat(body.has(forbidden)).as("không được gửi %s", forbidden).isFalse();
        }
    }

    @Test
    void buildParams_withoutServerFallbackOmitsFallbacksAndBeta() {
        AiProperties properties = new AiProperties(false, "claude-sonnet-5-5", AiEffort.HIGH, 1024,
                Duration.ofSeconds(60), 1, false, true);
        MessageCreateParams params = new AnthropicAiClient(sdkClient, properties).buildParams(REQUEST);
        JsonNode body = ObjectMappers.jsonMapper().valueToTree(params._body());

        assertThat(body.has("fallbacks")).isFalse();
        assertThat(params.betas()).isEmpty();
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("high");
    }

    private BetaMessage message(BetaStopReason stopReason, String... texts) {
        BetaMessage message = mock(BetaMessage.class);
        BetaUsage usage = mock(BetaUsage.class);
        when(usage.inputTokens()).thenReturn(120L);
        when(usage.outputTokens()).thenReturn(45L);
        when(message.usage()).thenReturn(usage);
        when(message.stopReason()).thenReturn(Optional.ofNullable(stopReason));
        List<BetaContentBlock> blocks = new ArrayList<>();
        for (String text : texts) {
            BetaTextBlock textBlock = mock(BetaTextBlock.class);
            when(textBlock.text()).thenReturn(text);
            BetaContentBlock block = mock(BetaContentBlock.class);
            when(block.text()).thenReturn(Optional.of(textBlock));
            blocks.add(block);
        }
        when(message.content()).thenReturn(blocks);
        return message;
    }

    @Test
    void endTurn_returnsJoinedTextAndTokenCounts() {
        BetaMessage response = message(BetaStopReason.END_TURN, "Vào Kho, ", "bấm Thêm kho.");
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(response);

        AiReply reply = client.ask(REQUEST);

        assertThat(reply).isEqualTo(AiReply.answered("Vào Kho, bấm Thêm kho.", "end_turn", 120, 45, false));
    }

    @Test
    void maxTokens_returnsPartialTextMarkedTruncated() {
        BetaMessage response = message(BetaStopReason.MAX_TOKENS, "Bước 1: vào Kho");
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(response);

        AiReply reply = client.ask(REQUEST);

        assertThat(reply.truncated()).isTrue();
        assertThat(reply.refused()).isFalse();
        assertThat(reply.text()).isEqualTo("Bước 1: vào Kho");
    }

    @Test
    void refusal_returnsPoliteVietnameseMessageWithoutReadingContent() {
        BetaMessage response = message(BetaStopReason.REFUSAL, "nội dung không được dùng");
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(response);

        AiReply reply = client.ask(REQUEST);

        assertThat(reply.refused()).isTrue();
        assertThat(reply.text()).isEqualTo(AiReply.REFUSAL_MESSAGE);
        assertThat(reply.stopReason()).isEqualTo("refusal");
    }

    @Test
    void emptyAnswer_isServiceUnavailable() {
        BetaMessage response = message(BetaStopReason.END_TURN);
        when(messageService.create(any(MessageCreateParams.class))).thenReturn(response);

        assertServiceUnavailable("không trả về nội dung");
    }

    @Test
    void rateLimit_isServiceUnavailableWithoutProviderPayload() {
        when(messageService.create(any(MessageCreateParams.class))).thenThrow(RateLimitException.builder()
                .headers(Headers.builder().build())
                .body(JsonValue.from(Map.of("error", Map.of("message", "chi tiết của nhà cung cấp"))))
                .build());

        ApiException ex = assertServiceUnavailable("quá tải");
        assertThat(ex.getMessage()).doesNotContain("chi tiết của nhà cung cấp");
    }

    @Test
    void invalidKey_isServiceUnavailable() {
        when(messageService.create(any(MessageCreateParams.class))).thenThrow(UnauthorizedException.builder()
                .headers(Headers.builder().build())
                .body(JsonValue.from(Map.of()))
                .build());

        assertServiceUnavailable("khóa truy cập");
    }

    @Test
    void timeout_isServiceUnavailableAndSaysNotCharged() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("Request failed", new SocketTimeoutException("timeout")));

        assertServiceUnavailable("không phản hồi trong 60 giây, lượt này không bị tính");
    }

    @Test
    void connectionFailure_isServiceUnavailable() {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("Request failed", new ConnectException("refused")));

        assertServiceUnavailable("Không kết nối được");
    }

    private ApiException assertServiceUnavailable(String messagePart) {
        Throwable thrown = catchThrowable(() -> client.ask(REQUEST));
        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException ex = (ApiException) thrown;
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(ex.getMessage()).contains(messagePart);
        return ex;
    }

    // ------------------------------------------------------------------ generateJson (trích xuất, WMS-24.2)

    private static final Map<String, Object> SCHEMA = Map.of("type", "object",
            "properties", Map.of("ok", Map.of("type", "boolean")),
            "required", List.of("ok"), "additionalProperties", false);

    private static AiJsonRequest jsonRequest(AiContentPart... parts) {
        return new AiJsonRequest("Lời dặn cố định", List.of(parts), SCHEMA, 32000, Duration.ofSeconds(180));
    }

    @Test
    void buildJsonParams_sendsSchemaDocumentImageAndCachedSystemWithoutSamplingParameters() {
        MessageCreateParams params = client.buildJsonParams(jsonRequest(
                new AiContentPart.Pdf("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)),
                new AiContentPart.Image(new byte[]{1, 2, 3}, "image/png"),
                new AiContentPart.Text("Trích xuất danh sách")));
        JsonNode body = ObjectMappers.jsonMapper().valueToTree(params._body());

        assertThat(body.path("model").asText()).isEqualTo("claude-sonnet-5-5");
        assertThat(body.path("max_tokens").asLong()).isEqualTo(32000);
        assertThat(body.path("system").get(0).path("text").asText()).isEqualTo("Lời dặn cố định");
        assertThat(body.path("system").get(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        JsonNode format = body.path("output_config").path("format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("schema").path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(format.path("schema").path("required").get(0).asText()).isEqualTo("ok");
        JsonNode content = body.path("messages").get(0).path("content");
        assertThat(content.get(0).path("type").asText()).isEqualTo("document");
        assertThat(content.get(0).path("source").path("type").asText()).isEqualTo("base64");
        assertThat(content.get(0).path("source").path("media_type").asText()).isEqualTo("application/pdf");
        assertThat(content.get(0).path("source").path("data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertThat(content.get(1).path("type").asText()).isEqualTo("image");
        assertThat(content.get(1).path("source").path("media_type").asText()).isEqualTo("image/png");
        assertThat(content.get(2).path("type").asText()).isEqualTo("text");
        assertThat(content.get(2).path("text").asText()).isEqualTo("Trích xuất danh sách");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        for (String forbidden : List.of("temperature", "top_p", "top_k", "thinking", "budget_tokens")) {
            assertThat(body.has(forbidden)).as("không được gửi %s", forbidden).isFalse();
        }
    }

    private static BetaRawMessageStreamEvent event(String json) {
        try {
            return ObjectMappers.jsonMapper().readValue(json, BetaRawMessageStreamEvent.class);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static StreamResponse<BetaRawMessageStreamEvent> stream(String stopReason, String... textDeltas) {
        List<BetaRawMessageStreamEvent> events = new ArrayList<>();
        events.add(event("""
                {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant",
                 "model":"claude-sonnet-5-5","content":[],"stop_reason":null,"stop_sequence":null,
                 "usage":{"input_tokens":900,"output_tokens":1}}}"""));
        events.add(event("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"));
        for (String delta : textDeltas) {
            events.add(event("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":"
                    + ObjectMappers.jsonMapper().valueToTree(delta) + "}}"));
        }
        events.add(event("{\"type\":\"content_block_stop\",\"index\":0}"));
        events.add(event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason
                + "\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":42}}"));
        events.add(event("{\"type\":\"message_stop\"}"));
        return new StreamResponse<>() {
            @Override
            public java.util.stream.Stream<BetaRawMessageStreamEvent> stream() {
                return events.stream();
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void generateJson_streamsAndAccumulatesTheFinalMessageWithRequestTimeout() {
        when(messageService.createStreaming(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(stream("end_turn", "{\"ok\":", "true}"));

        AiJsonReply reply = client.generateJson(jsonRequest(new AiContentPart.Text("x")));

        assertThat(reply).isEqualTo(AiJsonReply.completed("{\"ok\":true}", "end_turn", 900, 42));
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(messageService).createStreaming(any(MessageCreateParams.class), options.capture());
        assertThat(options.getValue().timeout()).hasValueSatisfying(
                timeout -> assertThat(timeout.request()).isEqualTo(Duration.ofSeconds(180)));
    }

    @Test
    void generateJson_maxTokensIsMarkedTruncatedAndRefusalSkipsContent() {
        when(messageService.createStreaming(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(stream("max_tokens", "{\"rows\":["))
                .thenReturn(stream("refusal", "không dùng"));

        AiJsonReply truncated = client.generateJson(jsonRequest(new AiContentPart.Text("x")));
        AiJsonReply refused = client.generateJson(jsonRequest(new AiContentPart.Text("x")));

        assertThat(truncated.truncated()).isTrue();
        assertThat(truncated.refused()).isFalse();
        assertThat(refused.refused()).isTrue();
        assertThat(refused.json()).isEmpty();
    }

    @Test
    void generateJson_sdkErrorsAreServiceUnavailableWithExtractionTimeout() {
        when(messageService.createStreaming(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenThrow(new AnthropicIoException("Request failed", new SocketTimeoutException("timeout")));

        Throwable thrown = catchThrowable(() -> client.generateJson(jsonRequest(new AiContentPart.Text("x"))));

        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
            assertThat(ex.getMessage()).contains("180 giây, lượt này không bị tính");
        });
    }

    @Test
    void close_closesSdkClient() {
        client.close();

        verify(sdkClient).close();
    }
}
