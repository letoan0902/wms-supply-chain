package vn.wms.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cấu hình bản tóm tắt số liệu điều hành bằng AI ({@code app.ai.summary.*}, WMS-18.1). Mọi giá trị có mặc định.
 *
 * @param cacheTtl  thời gian giữ bản tóm tắt trên Redis, để mở dashboard nhiều lần không tốn thêm lượt AI
 * @param maxTokens số token đầu ra tối đa của một lần tóm tắt
 * @param timeout   thời gian chờ tối đa của lần gọi Claude
 */
@Validated
@ConfigurationProperties(prefix = "app.ai.summary")
public record AiSummaryProperties(
        @DefaultValue("30m") @NotNull Duration cacheTtl,
        @DefaultValue("1500") @Min(256) @Max(8000) long maxTokens,
        @DefaultValue("60s") @NotNull Duration timeout) {
}
