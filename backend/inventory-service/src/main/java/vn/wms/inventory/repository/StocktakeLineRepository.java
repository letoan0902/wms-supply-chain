package vn.wms.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StocktakeLine;

import java.util.Collection;
import java.util.List;

public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, Long> {

    @Query("SELECT l FROM StocktakeLine l JOIN FETCH l.binLocation b WHERE l.session.id = :sessionId ORDER BY b.code ASC, l.productId ASC, l.id ASC")
    List<StocktakeLine> findBySessionIdOrdered(@Param("sessionId") Long sessionId);

    long countBySessionId(Long sessionId);

    /**
     * Khóa bi quan (SELECT ... FOR UPDATE) mọi dòng tồn tại các vị trí vừa đóng băng để chụp tồn: chờ các giao dịch
     * nhập, xuất đang chạy trên các dòng đó commit xong rồi mới đọc, nên ảnh chụp luôn là số đã commit mới nhất.
     * Đặt ở đây (không ở StockItemRepository) để không đụng vào repository dùng chung của các luồng tồn kho khác.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.binLocation.id IN :binLocationIds ORDER BY s.id ASC")
    List<StockItem> lockStockItemsInBins(@Param("binLocationIds") Collection<Long> binLocationIds);

    long countBySessionIdAndCountedQuantityIsNull(Long sessionId);

    /** Dòng của đợt theo đúng bộ khóa (vị trí, sản phẩm, số lô); số lô null chỉ khớp với dòng không có số lô. */
    @Query("SELECT l FROM StocktakeLine l WHERE l.session.id = :sessionId AND l.binLocation.id = :binLocationId " +
           "AND l.productId = :productId AND ((:lotNo IS NULL AND l.lotNo IS NULL) OR l.lotNo = :lotNo) ORDER BY l.id ASC")
    List<StocktakeLine> findByKey(@Param("sessionId") Long sessionId,
                                  @Param("binLocationId") Long binLocationId,
                                  @Param("productId") Long productId,
                                  @Param("lotNo") String lotNo);

    /**
     * Tìm kiếm dòng kiểm kê có phân trang. {@code counted}: true chỉ dòng đã đếm, false chỉ dòng chưa đếm;
     * {@code onlyVariance}: chỉ dòng có chênh lệch khác 0 (sau khi đã tính chênh lệch).
     */
    @Query(value = "SELECT l FROM StocktakeLine l JOIN FETCH l.binLocation b " +
           "WHERE l.session.id = :sessionId " +
           "  AND (:productId IS NULL OR l.productId = :productId) " +
           "  AND (:binCode IS NULL OR :binCode = '' OR LOWER(b.code) LIKE LOWER(CONCAT('%', :binCode, '%'))) " +
           "  AND (:counted IS NULL OR (:counted = true AND l.countedQuantity IS NOT NULL) " +
           "                        OR (:counted = false AND l.countedQuantity IS NULL)) " +
           "  AND (:onlyVariance = false OR (l.varianceQuantity IS NOT NULL AND l.varianceQuantity <> 0)) " +
           "ORDER BY b.code ASC, l.productId ASC, l.id ASC",
           countQuery = "SELECT COUNT(l) FROM StocktakeLine l JOIN l.binLocation b " +
           "WHERE l.session.id = :sessionId " +
           "  AND (:productId IS NULL OR l.productId = :productId) " +
           "  AND (:binCode IS NULL OR :binCode = '' OR LOWER(b.code) LIKE LOWER(CONCAT('%', :binCode, '%'))) " +
           "  AND (:counted IS NULL OR (:counted = true AND l.countedQuantity IS NOT NULL) " +
           "                        OR (:counted = false AND l.countedQuantity IS NULL)) " +
           "  AND (:onlyVariance = false OR (l.varianceQuantity IS NOT NULL AND l.varianceQuantity <> 0))")
    Page<StocktakeLine> search(@Param("sessionId") Long sessionId,
                               @Param("productId") Long productId,
                               @Param("binCode") String binCode,
                               @Param("counted") Boolean counted,
                               @Param("onlyVariance") boolean onlyVariance,
                               Pageable pageable);
}
