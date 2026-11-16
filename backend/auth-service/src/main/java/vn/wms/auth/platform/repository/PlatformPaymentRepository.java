package vn.wms.auth.platform.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.payment.entity.Payment;
import vn.wms.auth.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Truy vấn thanh toán xuyên doanh nghiệp cho quản trị nền tảng, luôn loại giao dịch của doanh nghiệp hệ thống
 * {@code PLATFORM} (xem {@link PlatformCompanyRepository}).
 */
public interface PlatformPaymentRepository extends Repository<Payment, Long> {

    /** Điều kiện loại doanh nghiệp hệ thống, dùng chung cho các câu truy vấn có bí danh {@code p}. */
    String NOT_PLATFORM = "NOT EXISTS (SELECT 1 FROM Company c WHERE c.id = p.companyId AND c.code = '"
            + Company.PLATFORM_CODE + "')";

    String SEARCH_WHERE = " WHERE " + NOT_PLATFORM
            + " AND (:status IS NULL OR p.status = :status)"
            + " AND (:companyId IS NULL OR p.companyId = :companyId)"
            + " AND p.createdAt >= :fromInclusive AND p.createdAt < :toExclusive";

    /** Số tiền và thời điểm thanh toán của các giao dịch theo trạng thái, mỗi dòng {@code [Long amount, Instant paidAt]}. */
    @Query("SELECT p.amount, p.paidAt FROM Payment p WHERE p.status = :status AND " + NOT_PLATFORM)
    List<Object[]> findAmountAndPaidAtByStatus(@Param("status") PaymentStatus status);

    /** Giao dịch theo trạng thái, thứ tự và số lượng theo {@code pageable} (không đếm tổng). */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND " + NOT_PLATFORM)
    List<Payment> findByStatus(@Param("status") PaymentStatus status, Pageable pageable);

    /** Lọc giao dịch theo trạng thái, doanh nghiệp ({@code null} là không lọc) và khoảng thời điểm tạo {@code [from, to)}. */
    @Query(value = "SELECT p FROM Payment p" + SEARCH_WHERE,
            countQuery = "SELECT COUNT(p) FROM Payment p" + SEARCH_WHERE)
    Page<Payment> search(@Param("status") PaymentStatus status,
                         @Param("companyId") Long companyId,
                         @Param("fromInclusive") Instant fromInclusive,
                         @Param("toExclusive") Instant toExclusive,
                         Pageable pageable);

    /**
     * Chuyển mọi giao dịch PENDING đã quá hạn sang EXPIRED, cùng quy tắc với {@code PaymentRepository#expireOverdue}
     * của từng doanh nghiệp (điều kiện {@code status = PENDING} nên không ghi đè giao dịch vừa được webhook chuyển PAID).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :expired, p.updatedAt = :now "
            + "WHERE p.status = :pending AND p.expiresAt < :now")
    int expireAllOverdue(@Param("now") Instant now, @Param("pending") PaymentStatus pending,
                         @Param("expired") PaymentStatus expired);
}
