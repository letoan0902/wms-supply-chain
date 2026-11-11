package vn.wms.auth.payment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.auth.payment.entity.Payment;
import vn.wms.auth.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderCode(Long orderCode);

    Optional<Payment> findByOrderCodeAndCompanyId(Long orderCode, Long companyId);

    /** Đọc giao dịch và khóa dòng (SELECT ... FOR UPDATE) để webhook gửi lặp hoặc gửi đồng thời không kích hoạt hai lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderCode = :orderCode")
    Optional<Payment> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    Page<Payment> findByCompanyId(Long companyId, Pageable pageable);

    /**
     * Chuyển các giao dịch PENDING đã quá hạn của một doanh nghiệp sang EXPIRED. Dùng câu UPDATE có điều kiện
     * {@code status = PENDING} để không bao giờ ghi đè một giao dịch vừa được webhook chuyển PAID.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = :expired, p.updatedAt = :now where p.companyId = :companyId "
            + "and p.status = :pending and p.expiresAt < :now")
    int expireOverdue(@Param("companyId") Long companyId, @Param("now") Instant now,
                      @Param("pending") PaymentStatus pending, @Param("expired") PaymentStatus expired);
}
