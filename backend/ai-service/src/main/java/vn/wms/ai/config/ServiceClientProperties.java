package vn.wms.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Thời gian chờ khi ai-service gọi service khác qua {@link ServiceClientConfig} ({@code app.ai.services.*}). Mọi giá
 * trị có mặc định, không cần biến môi trường mới. Service phía sau treo thì lời gọi dừng sau thời gian chờ thay vì giữ
 * request của người dùng tới khi nginx cắt (200 giây).
 *
 * @param connectTimeout  thời gian chờ mở kết nối, mặc định 3 giây
 * @param readTimeout     thời gian chờ đọc phản hồi của các lời gọi tra cứu (trợ lý AI, bản tóm tắt dashboard),
 *                        mặc định 10 giây
 * @param bulkReadTimeout thời gian chờ đọc phản hồi của API nhập hàng loạt (tối đa 200 dòng một lần, có kiểm tra
 *                        giới hạn gói), mặc định 120 giây, nhỏ hơn 200 giây nginx chờ {@code /api/ai/}
 */
@ConfigurationProperties(prefix = "app.ai.services")
public record ServiceClientProperties(Duration connectTimeout, Duration readTimeout, Duration bulkReadTimeout) {

    public ServiceClientProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(3);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
        bulkReadTimeout = bulkReadTimeout != null ? bulkReadTimeout : Duration.ofSeconds(120);
    }
}
