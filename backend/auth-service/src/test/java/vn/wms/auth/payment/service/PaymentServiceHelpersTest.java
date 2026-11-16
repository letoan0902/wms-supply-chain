package vn.wms.auth.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.wms.auth.payment.payos.MockPayOsClient;
import vn.wms.auth.payment.payos.PayOsClient;
import vn.wms.auth.payment.payos.PayOsProperties;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Mã đơn, nội dung chuyển khoản và liên kết giả lập của WMS-23.5a. */
class PaymentServiceHelpersTest {

    @Test
    @DisplayName("Mã đơn là số nguyên dương 16 chữ số, không vượt giới hạn của PayOS")
    void orderCodeFormat() {
        long before = System.currentTimeMillis() * 1000L;
        long code = PaymentService.nextOrderCode();
        long after = (System.currentTimeMillis() + 1) * 1000L;
        assertThat(code).isBetween(before, after).isLessThanOrEqualTo(PaymentService.MAX_ORDER_CODE);
        assertThat(String.valueOf(code)).hasSize(16);
    }

    @Test
    @DisplayName("Nội dung chuyển khoản là WMS PRO, tối đa 9 ký tự (giới hạn của PayOS với tài khoản ngân hàng không liên kết qua PayOS)")
    void descriptionFormat() {
        String description = PaymentService.descriptionOf("PRO");
        assertThat(description).isEqualTo("WMS PRO").hasSizeLessThanOrEqualTo(9);
        // Mã gói dài hơn cũng bị cắt về 9 ký tự
        assertThat(PaymentService.descriptionOf("ENTERPRISE")).isEqualTo("WMS ENTER").hasSize(9);
    }

    @Test
    @DisplayName("Liên kết giả lập trỏ tới trang /payment/mock của frontend, lấy gốc từ PAYOS_RETURN_URL")
    void mockCheckoutUrl() {
        PayOsProperties properties = new PayOsProperties(null, null, null,
                "https://wms.example.com/payment/result?from=payos", null, null, true);
        PayOsClient.PaymentLink link = new MockPayOsClient(properties).createPaymentLink(
                new PayOsClient.PaymentLinkRequest(1762761600123457L, 499000, "WMS PRO 1762761600123457", Instant.now()));
        assertThat(link.checkoutUrl()).isEqualTo("https://wms.example.com/payment/mock?orderCode=1762761600123457");
        assertThat(link.qrCode()).isNull();
    }
}
