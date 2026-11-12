package vn.wms.inventory.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình bộ nhớ đệm tổng tồn và tồn khả dụng trên Redis (WMS-19.1), tiền tố {@code app.stock-cache}.
 *
 * <ul>
 *   <li>{@code enabled} (mặc định {@code true}): tắt thì mọi API đọc thẳng cơ sở dữ liệu, không gọi Redis lần nào.</li>
 *   <li>{@code ttl} (mặc định 60 giây): thời gian sống của một giá trị đệm. Chỉ là lưới an toàn: giá trị đã bị vô hiệu
 *       ngay sau khi giao dịch thay đổi tồn commit.</li>
 *   <li>{@code retry-after} (mặc định 30 giây): Redis lỗi thì bỏ qua bộ nhớ đệm trong khoảng này rồi mới thử lại, để
 *       request không phải chờ Redis lỗi lần nữa; cảnh báo cũng chỉ ghi tối đa một dòng mỗi khoảng.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.stock-cache")
public class StockCacheProperties {

    private boolean enabled = true;
    private Duration ttl = Duration.ofSeconds(60);
    private Duration retryAfter = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    public void setRetryAfter(Duration retryAfter) {
        this.retryAfter = retryAfter;
    }
}
