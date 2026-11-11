package vn.wms.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cấu hình gọi Claude của ai-service ({@code app.ai.*}). Khóa API KHÔNG nằm ở đây: SDK tự đọc biến môi trường
 * {@code ANTHROPIC_API_KEY}, service chỉ kiểm tra biến đó có giá trị hay không để chọn chế độ chạy.
 *
 * @param mock            {@code true} (mặc định, biến {@code AI_MOCK}): trả câu trả lời mẫu, không gọi Claude
 * @param model           mã mô hình Claude (biến {@code AI_MODEL}), mặc định {@code claude-sonnet-5-5}
 * @param effort          mức suy nghĩ gửi qua {@code output_config.effort} (biến {@code AI_EFFORT}): low, medium, high
 * @param maxTokens       số token đầu ra tối đa của một câu trả lời (biến {@code AI_MAX_TOKENS})
 * @param timeout         thời gian chờ tối đa của một lần gọi Claude; quá hạn thì báo lỗi và không trừ lượt
 * @param maxRetries      số lần SDK tự thử lại khi gặp lỗi tạm thời (429, 5xx, mất kết nối)
 * @param serverFallback  bật tham số {@code fallbacks: "default"} (beta {@code server-side-fallback-2026-07-01}):
 *                        câu hỏi bị mô hình chính từ chối được Anthropic chạy lại trên mô hình dự phòng
 * @param mockCountsUsage câu trả lời giả lập có bị tính lượt AI hay không (mặc định có, để thử được giới hạn gói)
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("true") boolean mock,
        @DefaultValue("claude-sonnet-5-5") @NotBlank String model,
        @DefaultValue("low") @NotNull AiEffort effort,
        @DefaultValue("4096") @Min(1) @Max(64000) long maxTokens,
        @DefaultValue("60s") @NotNull Duration timeout,
        @DefaultValue("1") @Min(0) @Max(5) int maxRetries,
        @DefaultValue("true") boolean serverFallback,
        @DefaultValue("true") boolean mockCountsUsage) {
}
