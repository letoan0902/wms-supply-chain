package vn.wms.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cấu hình trích xuất tài liệu ({@code app.ai.extraction.*}, WMS-24.2). Giới hạn 10 MB và 200 dòng mỗi file là quy
 * tắc nghiệp vụ trong đặc tả nên để thành hằng số ở {@code vn.wms.ai.extraction.ExtractionLimits}, không cấu hình.
 *
 * @param maxTokens        số token đầu ra tối đa khi Claude đọc PDF, hình ảnh (trả cả bảng tới 200 dòng)
 * @param mappingMaxTokens số token đầu ra tối đa khi Claude chỉ ghép cột của file Excel, CSV
 * @param timeout          thời gian chờ tối đa của một lần gọi Claude để trích xuất; nginx của frontend
 *                         ({@code location /api/ai/}) phải chờ lâu hơn giá trị này
 * @param resultTtl        thời gian giữ kết quả trích xuất trên Redis để xem lại và bấm nhập
 */
@Validated
@ConfigurationProperties(prefix = "app.ai.extraction")
public record AiExtractionProperties(
        @DefaultValue("32000") @Min(1024) @Max(64000) long maxTokens,
        @DefaultValue("4096") @Min(512) @Max(16000) long mappingMaxTokens,
        @DefaultValue("180s") @NotNull Duration timeout,
        @DefaultValue("1h") @NotNull Duration resultTtl) {
}
