package vn.wms.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StocktakeSession;

import java.util.Optional;

public interface StocktakeSessionRepository extends JpaRepository<StocktakeSession, Long>,
        JpaSpecificationExecutor<StocktakeSession> {

    boolean existsByCode(String code);

    @Query("SELECT COUNT(s) FROM StocktakeSession s WHERE s.code LIKE CONCAT(:prefix, '%')")
    long countByCodePrefix(@Param("prefix") String prefix);

    /**
     * Khóa bi quan đợt kiểm kê khi đổi trạng thái hoặc ghi số đếm: các yêu cầu đồng thời trên cùng một đợt
     * (bấm duyệt hai lần, hai máy quét cùng đếm, kết thúc đếm trong lúc đang quét) chạy lần lượt,
     * yêu cầu sau thấy trạng thái mới nên không áp dụng điều chỉnh tồn hai lần.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StocktakeSession s WHERE s.id = :id")
    Optional<StocktakeSession> findByIdForUpdate(@Param("id") Long id);
}
