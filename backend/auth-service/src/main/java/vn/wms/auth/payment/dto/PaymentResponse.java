package vn.wms.auth.payment.dto;

import vn.wms.auth.payment.entity.Payment;

import java.time.Instant;

/**
 * Một giao dịch thanh toán trả cho frontend (tạo thanh toán, trang kết quả, lịch sử giao dịch).
 *
 * @param orderCode   mã đơn PayOS
 * @param plan        gói được thanh toán
 * @param cycle       {@code MONTHLY} hoặc {@code YEARLY}
 * @param amount      số tiền VND
 * @param status      {@code PENDING}, {@code PAID}, {@code CANCELLED}, {@code EXPIRED}
 * @param checkoutUrl trang thanh toán (PayOS hoặc trang giả lập)
 * @param qrCode      chuỗi VietQR, null ở chế độ giả lập
 * @param reference   mã giao dịch ngân hàng, có khi đã PAID
 * @param expiresAt   liên kết hết hạn lúc này
 * @param paidAt      thời điểm nhận tiền
 * @param createdAt   thời điểm tạo giao dịch
 */
public record PaymentResponse(
        Long orderCode,
        String plan,
        String cycle,
        Long amount,
        String status,
        String checkoutUrl,
        String qrCode,
        String reference,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getOrderCode(),
                payment.getPlanCode(),
                payment.getCycle().name(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getCheckoutUrl(),
                payment.getQrCode(),
                payment.getReference(),
                payment.getExpiresAt(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
