package vn.wms.ai.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiClientTest {

    private final MockAiClient client = new MockAiClient();

    @Test
    void answersInVietnameseEchoingTheQuestionWithZeroTokens() {
        AiReply reply = client.ask(new AiRequest("dặn", "Tạo kho mới thế nào?", 4096));

        assertThat(reply.text()).contains("Tạo kho mới thế nào?").contains("Chế độ giả lập");
        assertThat(reply.stopReason()).isEqualTo("end_turn");
        assertThat(reply.inputTokens()).isZero();
        assertThat(reply.outputTokens()).isZero();
        assertThat(reply.truncated()).isFalse();
        assertThat(reply.refused()).isFalse();
    }

    @Test
    void isDeterministic() {
        AiRequest request = new AiRequest("dặn", "Tồn kho SKU-001 là bao nhiêu?", 100);

        assertThat(client.ask(request)).isEqualTo(client.ask(request));
    }

    @Test
    void longQuestionIsShortenedAndWhitespaceCollapsed() {
        String question = "  Hàng   hóa\n" + "ạ".repeat(200);

        String excerpt = MockAiClient.excerpt(question);

        assertThat(excerpt).startsWith("Hàng hóa ").endsWith("...");
        assertThat(excerpt.codePointCount(0, excerpt.length())).isLessThanOrEqualTo(MockAiClient.EXCERPT_LENGTH + 3);
    }
}
