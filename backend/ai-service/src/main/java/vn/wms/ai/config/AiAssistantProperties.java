package vn.wms.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cấu hình trợ lý AI ({@code app.ai.assistant.*}, WMS-24.4). Mọi giá trị có mặc định, không cần biến môi trường mới.
 *
 * @param maxTokens          số token đầu ra tối đa của mỗi lần gọi Claude trong một câu hỏi
 * @param timeout            tổng thời gian cho một câu hỏi (mọi lần gọi Claude và mọi lần tra cứu); nginx của frontend
 *                           ({@code location /api/ai/}) chờ 200 giây nên giá trị này phải nhỏ hơn
 * @param maxToolRounds      số vòng tra cứu tối đa của một câu hỏi; hết vòng thì Claude trả lời bằng dữ liệu đã có
 * @param historyTtl         thời gian giữ lịch sử hội thoại trên Redis, tính từ câu hỏi gần nhất
 * @param maxExchanges       số lượt hỏi đáp gần nhất được giữ và gửi kèm câu hỏi mới
 * @param maxStoredAnswerLength số ký tự tối đa của một câu trả lời khi lưu vào lịch sử
 * @param maxListItems       số dòng tối đa của một danh sách trả về từ công cụ tra cứu (hàng hóa, tồn theo kho...)
 */
@Validated
@ConfigurationProperties(prefix = "app.ai.assistant")
public record AiAssistantProperties(
        @DefaultValue("2048") @Min(256) @Max(16000) long maxTokens,
        @DefaultValue("90s") @NotNull Duration timeout,
        @DefaultValue("5") @Min(0) @Max(10) int maxToolRounds,
        @DefaultValue("1h") @NotNull Duration historyTtl,
        @DefaultValue("10") @Min(1) @Max(50) int maxExchanges,
        @DefaultValue("4000") @Min(200) @Max(20000) int maxStoredAnswerLength,
        @DefaultValue("20") @Min(1) @Max(100) int maxListItems) {
}
