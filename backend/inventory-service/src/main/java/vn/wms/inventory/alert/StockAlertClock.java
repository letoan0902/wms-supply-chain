package vn.wms.inventory.alert;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Đồng hồ của phần cảnh báo: "hôm nay" tính theo múi giờ cấu hình ({@code app.alerts.zone}, mặc định giờ Việt Nam),
 * để tác vụ chạy lúc 00:00 giờ Việt Nam và hạn dùng được so với đúng ngày của người dùng dù máy chủ chạy UTC.
 * Tách thành bean riêng để test cố định được ngày.
 */
@Component
public class StockAlertClock {

    private final Clock clock;

    public StockAlertClock(StockAlertProperties properties) {
        this.clock = Clock.system(properties.zoneId());
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Instant now() {
        return clock.instant();
    }
}
