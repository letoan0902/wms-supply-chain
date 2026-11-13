package vn.wms.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.services.blocking.BetaService;
import com.anthropic.services.blocking.beta.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.wms.ai.AiTestSupport;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vòng hỏi đáp có công cụ của {@link AnthropicAiClient#chat(AiChatRequest, AiToolExecutor)} mà không gọi mạng: phản
 * hồi của Claude là JSON thật được SDK đọc thành {@link BetaMessage}, client của SDK là đối tượng giả lập.
 */
class AnthropicAiClientChatTest {

    private static final AiTool GET_STOCK = new AiTool("get_stock", "Tồn kho của một SKU",
            Map.of("sku", Map.of("type", "string")), List.of("sku"));

    private MessageService messageService;
    private AnthropicAiClient client;
    private final List<AiToolCall> executed = new ArrayList<>();
    private final AiToolExecutor executor = call -> {
        executed.add(call);
        return "get_stock".equals(call.name())
                ? AiToolResult.ok("{\"sku\":\"SP001\",\"availableQuantity\":12.5}")
                : AiToolResult.error("{\"status\":\"UNKNOWN_TOOL\"}");
    };

    @BeforeEach
    void setUp() {
        AnthropicClient sdkClient = mock(AnthropicClient.class);
        BetaService betaService = mock(BetaService.class);
        messageService = mock(MessageService.class);
        when(sdkClient.beta()).thenReturn(betaService);
        when(betaService.messages()).thenReturn(messageService);
        client = new AnthropicAiClient(sdkClient, AiTestSupport.properties(false, true));
    }

    private static AiChatRequest request(int maxToolRounds) {
        return new AiChatRequest("Lời dặn và tài liệu hướng dẫn",
                List.of(AiChatMessage.user("Xin chào"), AiChatMessage.assistant("Chào bạn")),
                "Tồn của SP001 còn bao nhiêu?", List.of(GET_STOCK), 2048, maxToolRounds, Duration.ofSeconds(90));
    }

    private static BetaMessage message(String stopReason, String contentJson) throws Exception {
        String json = """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5-5",
                 "content":%s,"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":100,"output_tokens":20}}
                """.formatted(contentJson, stopReason);
        return ObjectMappers.jsonMapper().readValue(json, BetaMessage.class);
    }

    private static BetaMessage toolUse(String id, String name, String inputJson) throws Exception {
        return message("tool_use", """
                [{"type":"text","text":"Để tôi tra cứu."},
                 {"type":"tool_use","id":"%s","name":"%s","input":%s}]""".formatted(id, name, inputJson));
    }

    private static BetaMessage answer(String text) throws Exception {
        return message("end_turn", "[{\"type\":\"text\",\"text\":\"" + text + "\"}]");
    }

    private List<JsonNode> sentBodies(int calls) {
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(messageService, times(calls)).create(captor.capture(), any(RequestOptions.class));
        return captor.getAllValues().stream()
                .map(params -> (JsonNode) ObjectMappers.jsonMapper().valueToTree(params._body()))
                .toList();
    }

    @Test
    void toolUseIsExecutedAndResultSentBackUntilFinalAnswer() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(toolUse("toolu_1", "get_stock", "{\"sku\":\"SP001\"}"))
                .thenReturn(answer("SP001 còn khả dụng 12,5 thùng."));

        AiChatReply reply = client.chat(request(5), executor);

        assertThat(reply.text()).isEqualTo("SP001 còn khả dụng 12,5 thùng.");
        assertThat(reply.refused()).isFalse();
        assertThat(reply.toolsUsed()).containsExactly("get_stock");
        assertThat(reply.inputTokens()).isEqualTo(200);
        assertThat(reply.outputTokens()).isEqualTo(40);
        assertThat(executed).hasSize(1);
        assertThat(executed.get(0).text("sku")).isEqualTo("SP001");

        List<JsonNode> bodies = sentBodies(2);
        JsonNode first = bodies.get(0);
        assertThat(first.path("model").asText()).isEqualTo("claude-sonnet-5-5");
        assertThat(first.path("system").get(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
        assertThat(first.path("tools").get(0).path("name").asText()).isEqualTo("get_stock");
        assertThat(first.path("tools").get(0).path("input_schema").path("additionalProperties").asBoolean(true))
                .isFalse();
        assertThat(first.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(first.has("tool_choice")).isFalse();
        for (String forbidden : List.of("temperature", "top_p", "top_k", "thinking", "budget_tokens")) {
            assertThat(first.has(forbidden)).as("không được gửi %s", forbidden).isFalse();
        }
        // Lịch sử (2 tin) + câu hỏi
        assertThat(first.path("messages")).hasSize(3);

        JsonNode second = bodies.get(1);
        JsonNode messages = second.path("messages");
        assertThat(messages).hasSize(5);
        // Tin của trợ lý được gửi lại nguyên vẹn, kể cả khối tool_use
        assertThat(messages.get(3).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(3).path("content").get(1).path("type").asText()).isEqualTo("tool_use");
        JsonNode toolResult = messages.get(4).path("content").get(0);
        assertThat(toolResult.path("type").asText()).isEqualTo("tool_result");
        assertThat(toolResult.path("tool_use_id").asText()).isEqualTo("toolu_1");
        assertThat(toolResult.path("is_error").asBoolean()).isFalse();
        assertThat(toolResult.path("content").asText()).contains("availableQuantity");
    }

    @Test
    void unknownToolRequestedByModelIsAnsweredAsErrorAndLoopContinues() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(toolUse("toolu_9", "delete_product", "{\"sku\":\"SP001\"}"))
                .thenReturn(answer("Tôi không thể xóa hàng hóa."));

        AiChatReply reply = client.chat(request(5), executor);

        assertThat(reply.text()).isEqualTo("Tôi không thể xóa hàng hóa.");
        JsonNode toolResult = sentBodies(2).get(1).path("messages").get(4).path("content").get(0);
        assertThat(toolResult.path("is_error").asBoolean()).isTrue();
        assertThat(toolResult.path("content").asText()).contains("UNKNOWN_TOOL");
    }

    @Test
    void toolLoopIsBoundedThenModelMustAnswerWithoutTools() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(toolUse("toolu_1", "get_stock", "{\"sku\":\"A1\"}"))
                .thenReturn(toolUse("toolu_2", "get_stock", "{\"sku\":\"A2\"}"))
                .thenReturn(answer("Đã tra 2 mã."));

        AiChatReply reply = client.chat(request(2), executor);

        assertThat(reply.text()).isEqualTo("Đã tra 2 mã.");
        assertThat(executed).hasSize(2);
        List<JsonNode> bodies = sentBodies(3);
        assertThat(bodies.get(0).has("tool_choice")).isFalse();
        assertThat(bodies.get(1).has("tool_choice")).isFalse();
        // Lần gọi cuối: hết vòng công cụ nên tool_choice none, danh sách công cụ giữ nguyên (không mất bộ nhớ đệm)
        assertThat(bodies.get(2).path("tool_choice").path("type").asText()).isEqualTo("none");
        assertThat(bodies.get(2).path("tools")).hasSize(1);
    }

    @Test
    void toolUseAfterBudgetIsNotExecutedAndTextIsReturned() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(toolUse("toolu_1", "get_stock", "{\"sku\":\"A1\"}"));

        AiChatReply reply = client.chat(request(0), executor);

        assertThat(executed).isEmpty();
        assertThat(reply.text()).isEqualTo("Để tôi tra cứu.");
        assertThat(sentBodies(1).get(0).path("tool_choice").path("type").asText()).isEqualTo("none");
    }

    @Test
    void truncatedTurnWithToolUseDoesNotRunTheTool() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(message("max_tokens", """
                        [{"type":"text","text":"Đang tra"},
                         {"type":"tool_use","id":"toolu_1","name":"get_stock","input":{"sku":"A"}}]"""));

        AiChatReply reply = client.chat(request(5), executor);

        assertThat(executed).isEmpty();
        assertThat(reply.truncated()).isTrue();
        assertThat(reply.text()).isEqualTo("Đang tra");
    }

    @Test
    void refusalStopsTheLoopWithPoliteMessage() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(message("refusal", "[]"));

        AiChatReply reply = client.chat(request(5), executor);

        assertThat(reply.refused()).isTrue();
        assertThat(reply.text()).isEqualTo(AiReply.REFUSAL_MESSAGE);
        assertThat(executed).isEmpty();
    }

    @Test
    void toolThrowingUnexpectedErrorBecomesErrorResult() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(toolUse("toolu_1", "get_stock", "{\"sku\":\"A1\"}"))
                .thenReturn(answer("Hệ thống chưa tra cứu được."));

        AiChatReply reply = client.chat(request(5), call -> {
            throw new IllegalStateException("lỗi bất ngờ");
        });

        assertThat(reply.text()).isEqualTo("Hệ thống chưa tra cứu được.");
        JsonNode toolResult = sentBodies(2).get(1).path("messages").get(4).path("content").get(0);
        assertThat(toolResult.path("is_error").asBoolean()).isTrue();
    }

    @Test
    void timeoutOfModelIsServiceUnavailable() {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenThrow(new AnthropicIoException("Request failed", new SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> client.chat(request(5), executor))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessage()).contains("lượt này không bị tính");
                });
    }

    @Test
    void emptyFinalAnswerIsServiceUnavailable() throws Exception {
        when(messageService.create(any(MessageCreateParams.class), any(RequestOptions.class)))
                .thenReturn(message("end_turn", "[]"));

        assertThatThrownBy(() -> client.chat(request(5), executor))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }
}
