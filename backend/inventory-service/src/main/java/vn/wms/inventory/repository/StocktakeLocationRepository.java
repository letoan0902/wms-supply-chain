package vn.wms.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StocktakeLocation;

import java.util.Collection;
import java.util.List;

public interface StocktakeLocationRepository extends JpaRepository<StocktakeLocation, Long> {

    List<StocktakeLocation> findBySessionId(Long sessionId);

    long countBySessionId(Long sessionId);

    /**
     * Các vị trí đang bị đóng băng trong danh sách, đọc có khóa chia sẻ (SELECT ... FOR SHARE) để luôn thấy
     * dữ liệu đã commit mới nhất: nghiệp vụ nhập, xuất, giữ chỗ, điều chuyển gọi trước khi đổi tồn, nên không
     * thể lọt qua giữa lúc một đợt kiểm kê vừa đóng băng vị trí và chụp tồn. Không join bảng khác để không khóa
     * dòng đợt kiểm kê.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT l FROM StocktakeLocation l WHERE l.frozenBinLocationId IN :binLocationIds")
    List<StocktakeLocation> findFrozenByBinLocationIdsForShare(@Param("binLocationIds") Collection<Long> binLocationIds);

    /** Các vị trí đang bị đóng băng trong danh sách (đọc thường, dùng để báo trùng khi bắt đầu kiểm kê). */
    @Query("SELECT l FROM StocktakeLocation l WHERE l.frozenBinLocationId IN :binLocationIds")
    List<StocktakeLocation> findFrozenByBinLocationIds(@Param("binLocationIds") Collection<Long> binLocationIds);

    @Query("SELECT COUNT(l) > 0 FROM StocktakeLocation l WHERE l.session.id = :sessionId AND l.binLocation.id = :binLocationId")
    boolean existsBySessionIdAndBinLocationId(@Param("sessionId") Long sessionId, @Param("binLocationId") Long binLocationId);
}
