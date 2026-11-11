package vn.wms.inventory.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * Cấu hình cảnh báo tồn kho (tiền tố {@code app.alerts}, WMS-15.1). Tài liệu yêu cầu chỉ chốt ngưỡng cận hạn 30 ngày
 * và giờ chạy 00:00; các giá trị còn lại là mặc định đơn giản, đổi được bằng biến môi trường.
 *
 * @param enabled               bật tác vụ quét hằng ngày (biến ALERT_SCAN_ENABLED)
 * @param cron                  lịch chạy, mặc định 00:00 mỗi ngày (biến ALERT_SCAN_CRON)
 * @param zone                  múi giờ của lịch chạy và của "hôm nay" khi tính hạn dùng
 * @param nearExpiryDays        lô có hạn dùng trong số ngày này (tính từ ngày mai) là cận hạn (biến ALERT_NEAR_EXPIRY_DAYS)
 * @param deadStockDays         mặt hàng không biến động từ số ngày này trở lên là hàng tồn lâu (biến ALERT_DEAD_STOCK_DAYS)
 * @param topItems              số dòng nghiêm trọng nhất đưa vào mỗi bản tổng hợp gửi đi
 * @param productPageSize       kích thước trang khi đọc danh mục sản phẩm từ product-service (tối đa 100)
 * @param maxProductPages       số trang tối đa đọc trong một lần (chặn vòng lặp vô hạn khi product-service trả sai)
 * @param productCacheSeconds   thời gian giữ danh mục sản phẩm trong bộ nhớ cho API đọc cảnh báo (tác vụ quét luôn đọc mới)
 */
@ConfigurationProperties(prefix = "app.alerts")
public record StockAlertProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 0 * * *") String cron,
        @DefaultValue("Asia/Ho_Chi_Minh") String zone,
        @DefaultValue("30") int nearExpiryDays,
        @DefaultValue("90") int deadStockDays,
        @DefaultValue("10") int topItems,
        @DefaultValue("100") int productPageSize,
        @DefaultValue("50") int maxProductPages,
        @DefaultValue("60") int productCacheSeconds) {

    public StockAlertProperties {
        if (nearExpiryDays < 1) {
            throw new IllegalArgumentException("app.alerts.near-expiry-days phải lớn hơn 0");
        }
        if (deadStockDays < 1) {
            throw new IllegalArgumentException("app.alerts.dead-stock-days phải lớn hơn 0");
        }
        topItems = Math.max(1, topItems);
        productPageSize = Math.min(100, Math.max(1, productPageSize));
        maxProductPages = Math.max(1, maxProductPages);
        productCacheSeconds = Math.max(0, productCacheSeconds);
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
