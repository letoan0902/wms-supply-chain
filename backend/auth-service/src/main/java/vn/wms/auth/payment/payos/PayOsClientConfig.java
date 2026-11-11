package vn.wms.auth.payment.payos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chọn bản cài đặt {@link PayOsClient} theo {@code PAYOS_MOCK}. */
@Slf4j
@Configuration
public class PayOsClientConfig {

    @Bean
    public PayOsClient payOsClient(PayOsProperties properties) {
        if (properties.mock()) {
            log.info("PayOS chạy ở chế độ giả lập (PAYOS_MOCK=true): không gọi cổng thanh toán thật");
            return new MockPayOsClient(properties);
        }
        if (!properties.hasCredentials()) {
            log.warn("PAYOS_MOCK=false nhưng chưa cấu hình đủ khóa PayOS: tạo thanh toán sẽ trả SERVICE_UNAVAILABLE");
        }
        return new HttpPayOsClient(properties);
    }
}
