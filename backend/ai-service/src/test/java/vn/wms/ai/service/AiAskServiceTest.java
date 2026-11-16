package vn.wms.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.wms.ai.AiTestSupport;
import vn.wms.ai.client.AiClient;
import vn.wms.ai.client.AiReply;
import vn.wms.ai.client.AiRequest;
import vn.wms.ai.dto.AskResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Thứ tự kiểm tra hạn mức, gọi mô hình, trừ lượt và các trường hợp không trừ lượt. */
class AiAskServiceTest {

    private final AiClient aiClient = mock(AiClient.class);
    private final AiQuotaService quotaService = mock(AiQuotaService.class);
    private final AiQuotaService.Reservation turn = mock(AiQuotaService.Reservation.class);
    private final AiAskService service = new AiAskService(aiClient, quotaService, AiTestSupport.properties(false, true));

    @BeforeEach
    void setUp() {
        when(quotaService.reserve()).thenReturn(turn);
    }

    @Test
    void successfulAnswer_isCountedAndReturnedWithUsage() {
        AiReply reply = AiReply.answered("Vào Kho, bấm Thêm kho.", "end_turn", 50, 12, false);
        when(aiClient.ask(any())).thenReturn(reply);
        when(turn.confirm(false)).thenReturn(4L);
        when(quotaService.currentMax()).thenReturn(20L);

        AskResponse response = service.ask("  Tạo kho thế nào?  ");

        assertThat(response).isEqualTo(new AskResponse("Vào Kho, bấm Thêm kho.", false, false,
                new AskResponse.Usage(4, 20L)));
        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiClient).ask(request.capture());
        assertThat(request.getValue().userMessage()).isEqualTo("Tạo kho thế nào?");
        assertThat(request.getValue().systemPrompt()).isEqualTo(AiPrompts.ASSISTANT_SYSTEM_PROMPT);
        assertThat(request.getValue().maxTokens()).isEqualTo(4096);
        verify(quotaService).reserve();
    }

    @Test
    void overQuota_doesNotCallTheModel() {
        when(quotaService.reserve()).thenThrow(new ApiException(ErrorCode.PLAN_LIMIT_EXCEEDED));

        assertThatThrownBy(() -> service.ask("Câu hỏi")).isInstanceOf(ApiException.class);
        verifyNoInteractions(aiClient);
        verifyNoInteractions(turn);
    }

    @Test
    void failedCall_isNotCounted() {
        when(aiClient.ask(any())).thenThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI không phản hồi"));

        assertThatThrownBy(() -> service.ask("Câu hỏi"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        // Lượt đã giữ được trả lại khi đóng (không confirm)
        verify(turn, never()).confirm(anyBoolean());
        verify(turn).close();
    }

    @Test
    void refusal_returnsPoliteMessageAndFlag() {
        AiReply refusal = AiReply.refusal("refusal", 40, 0);
        when(aiClient.ask(any())).thenReturn(refusal);
        when(turn.confirm(true)).thenReturn(3L);

        AskResponse response = service.ask("Câu hỏi nhạy cảm");

        assertThat(response.refused()).isTrue();
        assertThat(response.answer()).isEqualTo(AiReply.REFUSAL_MESSAGE);
        assertThat(response.usage().used()).isEqualTo(3L);
    }
}
