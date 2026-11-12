package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockItem;

import java.util.Collection;
import java.util.List;

/**
 * Truy vấn tồn theo số lô cho truy vết (WMS-17.2), tách khỏi StockItemRepository để không chạm vào file nhiều người
 * cùng sửa. JPQL nên được Hibernate tự lọc theo doanh nghiệp hiện tại (@TenantId).
 */
public interface LotStockItemRepository extends Repository<StockItem, Long> {

    /** Các dòng tồn của một số lô của một sản phẩm, kèm vị trí và kho. */
    @Query("SELECT s FROM StockItem s JOIN FETCH s.binLocation b JOIN FETCH b.warehouse " +
            "WHERE s.lotNo = :lotNo AND s.productId = :productId ORDER BY b.code ASC, s.id ASC")
    List<StockItem> findByLotNoAndProductId(@Param("lotNo") String lotNo, @Param("productId") Long productId);

    /** Các sản phẩm khác nhau có dòng tồn mang số lô này, trên mọi kho của doanh nghiệp. */
    @Query("SELECT DISTINCT s.productId FROM StockItem s WHERE s.lotNo = :lotNo")
    List<Long> findDistinctProductIdsByLotNo(@Param("lotNo") String lotNo);

    /** Như trên nhưng chỉ trong các kho được xem. */
    @Query("SELECT DISTINCT s.productId FROM StockItem s WHERE s.lotNo = :lotNo AND s.binLocation.warehouse.id IN :warehouseIds")
    List<Long> findDistinctProductIdsByLotNoInWarehouses(@Param("lotNo") String lotNo,
                                                         @Param("warehouseIds") Collection<Long> warehouseIds);
}
