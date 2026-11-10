package vn.wms.auth.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.common.persistence.AuditableEntity;

import java.time.Instant;

/**
 * Giao dịch thanh toán gói dịch vụ qua PayOS (bảng {@code payments}, migration V6).
 *
 * <p>Không dùng {@code @TenantId}: auth-service lọc tường minh theo {@code companyId} của người đang đăng nhập,
 * còn webhook của PayOS không mang doanh nghiệp nên phải tìm theo {@code orderCode}.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Mã đơn gửi PayOS: số nguyên dương, duy nhất toàn hệ thống. */
    @Column(name = "order_code", nullable = false, unique = true)
    private Long orderCode;

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private BillingCycle cycle;

    /** Số tiền VND, không có phần thập phân, lấy từ bảng plans lúc tạo giao dịch. */
    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "payment_link_id", length = 100)
    private String paymentLinkId;

    /** Mã giao dịch ngân hàng do PayOS gửi trong webhook. */
    @Column(length = 100)
    private String reference;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Liên kết thanh toán hết hạn lúc này (tạo giao dịch cộng 15 phút). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** ID tài khoản ADMIN đã tạo giao dịch. */
    @Column(name = "created_by_user")
    private Long createdByUser;
}
