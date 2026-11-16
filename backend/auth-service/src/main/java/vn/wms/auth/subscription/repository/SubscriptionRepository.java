package vn.wms.auth.subscription.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.auth.subscription.entity.Subscription;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByCompanyId(Long companyId);

    /**
     * Đọc thuê bao của doanh nghiệp và khóa dòng (SELECT ... FOR UPDATE) trước khi ghi, để kích hoạt gói từ webhook
     * PayOS, chuyển EXPIRED và hạ về FREE không ghi đè lên nhau dựa trên một bản đọc cũ.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.company.id = :companyId")
    Optional<Subscription> findByCompanyIdForUpdate(@Param("companyId") Long companyId);

    /** Như {@link #findByCompanyIdForUpdate} nhưng theo id thuê bao (tác vụ định kỳ). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") Long id);

    /** Id của mọi thuê bao, để tác vụ định kỳ xử lý từng thuê bao trong một giao dịch ngắn riêng. */
    @Query("select s.id from Subscription s order by s.id")
    List<Long> findAllIds();
}
