package vn.wms.auth.payment.payos;

import java.time.Instant;

/**
 * Cổng tạo liên kết thanh toán. Có hai bản cài đặt, chọn theo {@code PAYOS_MOCK} trong {@link PayOsClientConfig}:
 * {@link HttpPayOsClient} gọi PayOS thật, {@link MockPayOsClient} trả liên kết tới trang giả lập, không gọi mạng.
 */
public interface PayOsClient {

    /**
     * Tạo liên kết thanh toán cho một đơn.
     *
     * @throws vn.wms.common.exception.ApiException mã {@code SERVICE_UNAVAILABLE} khi PayOS lỗi hoặc chưa cấu hình khóa
     */
    PaymentLink createPaymentLink(PaymentLinkRequest request);

    /**
     * Dữ liệu tạo liên kết.
     *
     * @param orderCode   mã đơn (số nguyên dương, duy nhất)
     * @param amount      số tiền VND
     * @param description nội dung chuyển khoản, tối đa 25 ký tự
     * @param expiredAt   thời điểm liên kết hết hạn
     */
    record PaymentLinkRequest(long orderCode, long amount, String description, Instant expiredAt) {
    }

    /**
     * Liên kết PayOS trả về.
     *
     * @param checkoutUrl   trang thanh toán chuyển trình duyệt tới
     * @param qrCode        chuỗi VietQR để frontend tự vẽ mã QR (có thể null ở chế độ giả lập)
     * @param paymentLinkId mã liên kết phía PayOS
     */
    record PaymentLink(String checkoutUrl, String qrCode, String paymentLinkId) {
    }
}
