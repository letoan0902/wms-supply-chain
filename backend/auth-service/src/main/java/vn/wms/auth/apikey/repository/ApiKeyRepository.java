package vn.wms.auth.apikey.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.auth.apikey.entity.ApiKey;
import vn.wms.auth.company.entity.Company;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** Mọi key của doanh nghiệp (cả key đã thu hồi), mới nhất trước. */
    List<ApiKey> findByCompanyIdOrderByCreatedAtDescIdDesc(Long companyId);

    /** Key theo ID trong phạm vi một doanh nghiệp; key của doanh nghiệp khác coi như không tồn tại. */
    Optional<ApiKey> findByIdAndCompanyId(Long id, Long companyId);

    /** Số key đang hoạt động của doanh nghiệp. */
    long countByCompanyIdAndRevokedAtIsNull(Long companyId);

    /**
     * Khóa dòng doanh nghiệp (SELECT ... FOR UPDATE) trong giao dịch tạo key, để hai lần tạo đồng thời không cùng
     * đếm được 4 key rồi cùng tạo thành 6.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Company c where c.id = :companyId")
    Optional<Company> lockCompany(@Param("companyId") Long companyId);

    /**
     * Ghi thời điểm dùng gần nhất đọc từ Redis. Chỉ ghi khi mới hơn giá trị đang có, nên chạy lại nhiều lần
     * với cùng dữ liệu không làm lùi thời điểm.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ApiKey k set k.lastUsedAt = :usedAt where k.id = :id "
            + "and (k.lastUsedAt is null or k.lastUsedAt < :usedAt)")
    int updateLastUsedAt(@Param("id") Long id, @Param("usedAt") Instant usedAt);
}
