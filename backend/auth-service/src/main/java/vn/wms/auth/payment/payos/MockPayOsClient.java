package vn.wms.auth.payment.payos;

import java.net.URI;

/**
 * Bản giả lập của {@link PayOsClient} khi {@code PAYOS_MOCK=true}: không gọi mạng, trả liên kết tới trang giả lập
 * {@code <gốc frontend>/payment/mock?orderCode=...}. Gốc frontend lấy từ {@code PAYOS_RETURN_URL} (bỏ đường dẫn và query).
 * Trên trang đó người dùng bấm xác nhận thì frontend gọi {@code POST /api/auth/payments/{orderCode}/mock-confirm}.
 */
public class MockPayOsClient implements PayOsClient {

    private final String frontendBase;

    public MockPayOsClient(PayOsProperties properties) {
        this.frontendBase = frontendBase(properties.returnUrl());
    }

    @Override
    public PaymentLink createPaymentLink(PaymentLinkRequest request) {
        String checkoutUrl = frontendBase + "/payment/mock?orderCode=" + request.orderCode();
        return new PaymentLink(checkoutUrl, null, "mock-" + request.orderCode());
    }

    /** Gốc của địa chỉ trả về, ví dụ {@code https://wms.example.com/payment/result?x=1} thành {@code https://wms.example.com}. */
    static String frontendBase(String returnUrl) {
        try {
            URI uri = URI.create(returnUrl);
            if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                return uri.getScheme() + "://" + uri.getRawAuthority();
            }
        } catch (IllegalArgumentException ignored) {
            // Địa chỉ cấu hình sai thì dùng gốc mặc định của máy phát triển
        }
        return frontendBase(PayOsProperties.DEFAULT_RETURN_URL);
    }
}
