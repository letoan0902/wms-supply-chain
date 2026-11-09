package vn.wms.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockItem;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    List<StockItem> findByProductId(Long productId);

    List<StockItem> findByBinLocationId(Long binLocationId);

    @Query("SELECT COUNT(s) > 0 FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId AND s.quantity > 0")
    boolean existsActiveStockByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(s) > 0 FROM StockItem s WHERE s.binLocation.id = :binLocationId AND s.quantity > 0")
    boolean existsActiveStockByBinLocationId(@Param("binLocationId") Long binLocationId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockItem s")
    BigDecimal sumTotalQuantity();

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId")
    BigDecimal sumQuantityByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE s.binLocation.warehouse.id IN :warehouseIds")
    BigDecimal sumQuantityByWarehouseIds(@Param("warehouseIds") Collection<Long> warehouseIds);

    @Query("SELECT COUNT(DISTINCT s.productId) FROM StockItem s WHERE s.quantity > 0")
    long countDistinctActiveProducts();

    @Query("SELECT COUNT(DISTINCT s.productId) FROM StockItem s WHERE s.binLocation.warehouse.id IN :warehouseIds AND s.quantity > 0")
    long countDistinctActiveProductsByWarehouseIds(@Param("warehouseIds") Collection<Long> warehouseIds);

    @Query("SELECT COUNT(DISTINCT s.binLocation.id) FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId AND s.quantity > 0")
    long countOccupiedLocationsByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(DISTINCT s.binLocation.id) FROM StockItem s WHERE s.binLocation.warehouse.id IN :warehouseIds AND s.quantity > 0")
    long countOccupiedLocationsByWarehouseIds(@Param("warehouseIds") Collection<Long> warehouseIds);

    @Query("SELECT COUNT(DISTINCT s.binLocation.id) FROM StockItem s WHERE s.quantity > 0")
    long countTotalOccupiedLocations();

    @Query("SELECT s FROM StockItem s WHERE s.productId = :productId AND (s.quantity - s.reservedQuantity) > 0 ORDER BY s.id ASC")
    List<StockItem> findAvailableStockItemsByProductId(@Param("productId") Long productId);

    @Query("SELECT s FROM StockItem s WHERE s.productId = :productId AND s.binLocation.warehouse.id = :warehouseId AND (s.quantity - s.reservedQuantity) > 0 ORDER BY s.id ASC")
    List<StockItem> findAvailableStockItemsByProductIdAndWarehouseId(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    @Query("SELECT s.binLocation.id, COUNT(s.id), COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE s.binLocation.warehouse.id = :warehouseId GROUP BY s.binLocation.id")
    List<Object[]> findStockStatsByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT s.binLocation.id, COUNT(s.id), COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE s.binLocation.warehouse.id IN :warehouseIds GROUP BY s.binLocation.id")
    List<Object[]> findStockStatsByWarehouseIds(@Param("warehouseIds") Collection<Long> warehouseIds);

    @Query("SELECT COALESCE(SUM(s.quantity), 0), COALESCE(SUM(s.reservedQuantity), 0) FROM StockItem s WHERE s.productId = :productId")
    List<Object[]> getStockTotalsByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0), COALESCE(SUM(s.reservedQuantity), 0) FROM StockItem s WHERE s.productId = :productId AND s.binLocation.warehouse.id = :warehouseId")
    List<Object[]> getStockTotalsByProductIdAndWarehouseId(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    @Query("SELECT s.productId, COALESCE(SUM(s.quantity), 0), COALESCE(SUM(s.reservedQuantity), 0) FROM StockItem s WHERE s.productId IN :productIds GROUP BY s.productId")
    List<Object[]> getBatchStockTotalsByProductIds(@Param("productIds") List<Long> productIds);

    @Query("SELECT s.productId, COALESCE(SUM(s.quantity), 0), COALESCE(SUM(s.reservedQuantity), 0) FROM StockItem s WHERE s.productId IN :productIds AND s.binLocation.warehouse.id = :warehouseId GROUP BY s.productId")
    List<Object[]> getBatchStockTotalsByProductIdsAndWarehouseId(@Param("productIds") List<Long> productIds, @Param("warehouseId") Long warehouseId);

    // =========================================================================================
    // KHÓA BI QUAN (PESSIMISTIC LOCKING) CHỐNG ÂM KHO KHI XUẤT KHO / TRỪ TỒN ĐỒNG THỜI (WMS-12.2)
    // =========================================================================================

    /**
     * Khóa bi quan (SELECT ... FOR UPDATE) trên 1 bản ghi StockItem cụ thể theo ID.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id = :id")
    Optional<StockItem> findByIdWithPessimisticLock(@Param("id") Long id);

    /**
     * Khóa bi quan trên bản ghi StockItem tại một vị trí ô/kệ cụ thể của một sản phẩm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.binLocation.id = :binLocationId AND s.productId = :productId")
    Optional<StockItem> findByBinLocationIdAndProductIdWithPessimisticLock(
            @Param("binLocationId") Long binLocationId,
            @Param("productId") Long productId);

    /**
     * Khóa bi quan trên toàn bộ các bản ghi StockItem còn tồn thực tế (quantity > 0)
     * của một sản phẩm trong một kho cụ thể, sắp xếp theo ID tăng dần để tránh Deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.productId = :productId AND s.binLocation.warehouse.id = :warehouseId AND s.quantity > 0 ORDER BY s.id ASC")
    List<StockItem> findAvailableStockItemsByProductIdAndWarehouseIdWithPessimisticLock(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId);

    /**
     * Khóa bi quan trên toàn bộ các bản ghi StockItem còn tồn thực tế của một sản phẩm trên toàn hệ thống.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.productId = :productId AND s.quantity > 0 ORDER BY s.id ASC")
    List<StockItem> findAvailableStockItemsByProductIdWithPessimisticLock(@Param("productId") Long productId);

    /**
     * Khóa bi quan theo danh sách ID StockItem.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id IN :ids ORDER BY s.id ASC")
    List<StockItem> findAllByIdWithPessimisticLock(@Param("ids") Collection<Long> ids);
}
