package vn.wms.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.wms.ai.config.AiEffort;
import vn.wms.ai.config.AiProperties;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gọi Claude qua SDK Java chính thức của Anthropic ({@code com.anthropic:anthropic-java}).
 *
 * <p>Quy tắc theo API hiện tại của Claude:
 * <ul>
 *   <li>không gửi {@code temperature}, {@code top_p}, {@code top_k}, {@code budget_tokens}; không đặt
 *       {@code thinking} (mô hình tự suy nghĩ thích ứng), độ sâu điều khiển bằng {@code output_config.effort};</li>
 *   <li>lời dặn hệ thống đặt đầu yêu cầu và đánh dấu bộ nhớ đệm prompt, để các task sau thêm tài liệu dài vẫn
 *       đọc lại được từ bộ nhớ đệm;</li>
 *   <li>luôn xét {@code stop_reason} trước khi đọc nội dung: {@code refusal} trả lời xin lỗi soạn sẵn,
 *       {@code max_tokens} trả phần đã sinh kèm cờ {@code truncated};</li>
 *   <li>bật {@code fallbacks: "default"} (beta {@code server-side-fallback-2026-07-01}) khi
 *       {@code app.ai.server-fallback=true}, nên dùng đường {@code client.beta().messages()}.</li>
 * </ul>
 *
 * <p>Thông báo lỗi và log không bao giờ chứa khóa API, nội dung câu hỏi hay phản hồi thô của nhà cung cấp.
 */
public class AnthropicAiClient implements AiClient, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiClient.class);

    private final AnthropicClient client;
    private final AiProperties properties;

    public AnthropicAiClient(AnthropicClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public AiReply ask(AiRequest request) {
        MessageCreateParams params = buildParams(request);
        BetaMessage message;
        try {
            message = client.beta().messages().create(params);
        } catch (RateLimitException ex) {
            throw failure(ex, "Dịch vụ AI đang quá tải hoặc đã hết hạn mức của nhà cung cấp, vui lòng thử lại sau ít phút.");
        } catch (UnauthorizedException | PermissionDeniedException ex) {
            throw failure(ex, "Dịch vụ AI chưa được cấu hình đúng khóa truy cập, vui lòng báo quản trị viên.");
        } catch (BadRequestException | NotFoundException ex) {
            throw failure(ex, "Yêu cầu gửi tới dịch vụ AI không hợp lệ (mô hình hoặc tham số cấu hình), "
                    + "vui lòng báo quản trị viên.");
        } catch (InternalServerException ex) {
            throw failure(ex, "Dịch vụ AI đang gặp sự cố, vui lòng thử lại sau.");
        } catch (AnthropicServiceException ex) {
            throw failure(ex, "Dịch vụ AI tạm thời không phản hồi, vui lòng thử lại sau.");
        } catch (AnthropicIoException ex) {
            if (isTimeout(ex)) {
                log.warn("Claude không phản hồi trong {} giây (lượt không bị tính)", properties.timeout().toSeconds());
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Dịch vụ AI không phản hồi trong "
                        + properties.timeout().toSeconds() + " giây, lượt này không bị tính. Vui lòng thử lại.");
            }
            log.warn("Không kết nối được tới Claude: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Không kết nối được tới dịch vụ AI, vui lòng thử lại sau.");
        } catch (AnthropicException ex) {
            log.warn("Gọi Claude thất bại: {}", ex.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Dịch vụ AI tạm thời không phản hồi, vui lòng thử lại sau.");
        }
        return toReply(message);
    }

    /**
     * Dựng tham số gọi API. Tách riêng để kiểm thử không cần mạng (mô hình, số token, mức suy nghĩ, không có tham số
     * lấy mẫu).
     */
    MessageCreateParams buildParams(AiRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(request.maxTokens())
                // Lời dặn cố định đứng đầu và đánh dấu bộ nhớ đệm (mặc định 5 phút).
                .systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder()
                        .text(request.systemPrompt())
                        .cacheControl(BetaCacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(request.userMessage())
                .outputConfig(BetaOutputConfig.builder().effort(toSdkEffort(properties.effort())).build());
        if (properties.serverFallback()) {
            builder.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01).fallbacksDefault();
        }
        return builder.build();
    }

    /** Chuyển phản hồi của SDK thành {@link AiReply}, xét {@code stop_reason} trước khi đọc nội dung. */
    AiReply toReply(BetaMessage message) {
        BetaUsage usage = message.usage();
        long inputTokens = usage != null ? usage.inputTokens() : 0L;
        long outputTokens = usage != null ? usage.outputTokens() : 0L;
        BetaStopReason stopReason = message.stopReason().orElse(null);
        String stopReasonText = stopReason != null ? stopReason.asString() : "unknown";

        if (BetaStopReason.REFUSAL.equals(stopReason)) {
            String category = message.stopDetails()
                    .flatMap(details -> details.category().map(Object::toString))
                    .orElse("không rõ");
            log.info("Claude từ chối trả lời (nhóm {}), lượt không bị tính", category);
            return AiReply.refusal(stopReasonText, inputTokens, outputTokens);
        }

        String text = message.content().stream()
                .map(BetaContentBlock::text)
                .flatMap(Optional::stream)
                .map(BetaTextBlock::text)
                .collect(Collectors.joining());
        boolean truncated = BetaStopReason.MAX_TOKENS.equals(stopReason)
                || BetaStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(stopReason);
        if (text.isBlank()) {
            log.warn("Claude trả về câu trả lời rỗng (stop_reason={})", stopReasonText);
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Dịch vụ AI không trả về nội dung, lượt này không bị tính. Vui lòng thử lại.");
        }
        if (truncated) {
            log.info("Câu trả lời của Claude bị cắt (stop_reason={}, {} token đầu ra)", stopReasonText, outputTokens);
        }
        return AiReply.answered(text, stopReasonText, inputTokens, outputTokens, truncated);
    }

    static BetaOutputConfig.Effort toSdkEffort(AiEffort effort) {
        return switch (effort) {
            case LOW -> BetaOutputConfig.Effort.LOW;
            case MEDIUM -> BetaOutputConfig.Effort.MEDIUM;
            case HIGH -> BetaOutputConfig.Effort.HIGH;
        };
    }

    /** Ghi log ngắn (loại lỗi, mã HTTP) rồi trả lỗi 503 với thông báo tiếng Việt, không kèm phản hồi thô. */
    private static ApiException failure(AnthropicServiceException ex, String message) {
        log.warn("Gọi Claude thất bại: {} (HTTP {}, loại {})", ex.getClass().getSimpleName(), ex.statusCode(),
                ex.errorType().map(Object::toString).orElse("không rõ"));
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    /** Đóng kết nối HTTP của SDK khi Spring dừng service. */
    @Override
    public void close() {
        client.close();
    }
}
