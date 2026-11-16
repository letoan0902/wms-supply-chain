package vn.wms.auth.platform.dto;

import vn.wms.auth.company.entity.Company;
import vn.wms.auth.payment.entity.Payment;
import vn.wms.auth.payment.entity.PaymentStatus;
import vn.wms.auth.subscription.entity.BillingCycle;

import java.time.Instant;

/**
 * Một giao dịch thanh toán trong khu vực quản trị nền tảng, kèm mã và tên doanh nghiệp. Không có liên kết thanh toán,
 * mã QR hay mã giao dịch ngân hàng.
 *
 * @param amount số tiền VND (số nguyên đồng)
 */
public record PlatformPayment(
        Long orderCode,
        Long companyId,
        String companyCode,
        String companyName,
        String plan,
        BillingCycle cycle,
        Long amount,
        PaymentStatus status,
        Instant createdAt,
        Instant paidAt) {

    /** {@code company} có thể {@code null} nếu không tra được doanh nghiệp, khi đó mã và tên là {@code null}. */
    public static PlatformPayment of(Payment payment, Company company) {
        return new PlatformPayment(
                payment.getOrderCode(),
                payment.getCompanyId(),
                company != null ? company.getCode() : null,
                company != null ? company.getName() : null,
                payment.getPlanCode(),
                payment.getCycle(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getPaidAt());
    }
}
