package vn.wms.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockItem;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Query("SELECT s FROM StockItem s " +
           "WHERE s.productId = :productId " +
           "  AND (s.quantity - s.reservedQuantity) > 0 " +
           "  AND s.locked = false " +
           "  AND (s.expDate IS NULL OR s.expDate > CURRENT_DATE) " +
           "ORDER BY CASE WHEN s.expDate IS NULL THEN 1 ELSE 0 END ASC, s.expDate ASC, s.id ASC")
    List<StockItem> findAvailableStockItemsByProductId(@Param("productId") Long productId);

    @Query("SELECT s FROM StockItem s " +
           "WHERE s.productId = :productId " +
           "  AND s.binLocation.warehouse.id = :warehouseId " +
           "  AND (s.quantity - s.reservedQuantity) > 0 " +
           "  AND s.locked = false " +
           "  AND (s.expDate IS NULL OR s.expDate > CURRENT_DATE) " +
           "ORDER BY CASE WHEN s.expDate IS NULL THEN 1 ELSE 0 END ASC, s.expDate ASC, s.id ASC")
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
    // KHÓA BI QUAN (PESSIMISTIC LOCKING) & THUẬT TOÁN XUẤT FEFO/FIFO CHỐNG ÂM KHO (WMS-12.2, WMS-10.2)
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
     * của một sản phẩm trong một kho cụ thể.
     * Áp dụng thuật toán FEFO (First-Expired, First-Out: lô hết hạn sớm nhất ưu tiên xuất trước),
     * kết hợp FIFO (id ASC) cho các mặt hàng không có hạn sử dụng.
     * Tự động loại bỏ các lô bị khóa (locked = true) và các lô đã hết hạn (expDate <= CURRENT_DATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s " +
           "WHERE s.productId = :productId " +
           "  AND s.binLocation.warehouse.id = :warehouseId " +
           "  AND s.quantity > 0 " +
           "  AND s.locked = false " +
           "  AND (s.expDate IS NULL OR s.expDate > CURRENT_DATE) " +
           "ORDER BY CASE WHEN s.expDate IS NULL THEN 1 ELSE 0 END ASC, s.expDate ASC, s.id ASC")
    List<StockItem> findAvailableStockItemsByProductIdAndWarehouseIdWithPessimisticLock(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId);

    /**
     * Khóa bi quan trên toàn bộ các bản ghi StockItem còn tồn thực tế của một sản phẩm trên toàn hệ thống (FEFO/FIFO).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s " +
           "WHERE s.productId = :productId " +
           "  AND s.quantity > 0 " +
           "  AND s.locked = false " +
           "  AND (s.expDate IS NULL OR s.expDate > CURRENT_DATE) " +
           "ORDER BY CASE WHEN s.expDate IS NULL THEN 1 ELSE 0 END ASC, s.expDate ASC, s.id ASC")
    List<StockItem> findAvailableStockItemsByProductIdWithPessimisticLock(@Param("productId") Long productId);

    /**
     * Khóa bi quan theo danh sách ID StockItem.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id IN :ids ORDER BY s.id ASC")
    List<StockItem> findAllByIdWithPessimisticLock(@Param("ids") Collection<Long> ids);

    // =========================================================================================
    // QUẢN LÝ LÔ HÀNG (BATCH/LOT) & TỰ ĐỘNG KHÓA LÔ HẾT HẠN (WMS-10.2 - FR12)
    // =========================================================================================

    /**
     * Tìm các lô hàng đã hết hạn sử dụng nhưng chưa bị khóa để tự động khóa.
     */
    @Query("SELECT s FROM StockItem s " +
           "WHERE s.expDate IS NOT NULL " +
           "  AND s.expDate <= :today " +
           "  AND s.locked = false " +
           "  AND s.quantity > 0")
    List<StockItem> findExpiredUnlockedLots(@Param("today") LocalDate today);

    /**
     * Danh sách mọi doanh nghiệp đang có tồn kho hoặc lô hàng, dùng cho tác vụ định kỳ chạy lần lượt từng doanh nghiệp.
     * Cố ý dùng SQL gốc để KHÔNG bị lọc theo doanh nghiệp hiện tại (luồng @Scheduled không có doanh nghiệp).
     * Chỉ trả về ID doanh nghiệp, không trả dữ liệu nghiệp vụ.
     */
    @Query(value = "SELECT company_id FROM stock_items UNION SELECT company_id FROM inventory_lots", nativeQuery = true)
    List<Long> findAllCompanyIds();

    /**
     * Tìm các lô hàng cận hạn sử dụng (trong vòng N ngày tới).
     */
    @Query("SELECT s FROM StockItem s " +
           "WHERE s.expDate IS NOT NULL " +
           "  AND s.expDate > :today " +
           "  AND s.expDate <= :cutoffDate " +
           "  AND s.locked = false " +
           "  AND s.quantity > 0 " +
           "ORDER BY s.expDate ASC")
    List<StockItem> findExpiringSoonLots(
            @Param("today") LocalDate today,
            @Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Tìm kiếm và phân trang danh sách lô hàng theo kho, sản phẩm, mã lô, trạng thái khóa, hạn dùng.
     */
    @Query("SELECT s FROM StockItem s " +
           "WHERE (:warehouseId IS NULL OR s.binLocation.warehouse.id = :warehouseId) " +
           "  AND (:productId IS NULL OR s.productId = :productId) " +
           "  AND (:lotNo IS NULL OR :lotNo = '' OR LOWER(COALESCE(s.lotNo, '')) LIKE LOWER(CONCAT('%', :lotNo, '%'))) " +
           "  AND (:locked IS NULL OR s.locked = :locked) " +
           "  AND (:hasStock IS NULL OR (:hasStock = true AND s.quantity > 0) OR (:hasStock = false AND s.quantity = 0)) " +
           "  AND (:isExpired IS NULL OR (:isExpired = true AND s.expDate IS NOT NULL AND s.expDate <= :today) " +
           "                          OR (:isExpired = false AND (s.expDate IS NULL OR s.expDate > :today))) " +
           "  AND (:isExpiringSoon IS NULL OR (:isExpiringSoon = true AND s.expDate IS NOT NULL AND s.expDate > :today AND s.expDate <= :cutoffDate))")
    Page<StockItem> searchLots(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("lotNo") String lotNo,
            @Param("locked") Boolean locked,
            @Param("hasStock") Boolean hasStock,
            @Param("isExpired") Boolean isExpired,
            @Param("isExpiringSoon") Boolean isExpiringSoon,
            @Param("today") LocalDate today,
            @Param("cutoffDate") LocalDate cutoffDate,
            Pageable pageable);

    @Query("SELECT COUNT(s) FROM StockItem s WHERE (:warehouseId IS NULL OR s.binLocation.warehouse.id = :warehouseId)")
    long countTotalLots(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(s) FROM StockItem s WHERE s.quantity > 0 AND s.locked = false AND (s.expDate IS NULL OR s.expDate > :today) AND (:warehouseId IS NULL OR s.binLocation.warehouse.id = :warehouseId)")
    long countActiveLots(@Param("warehouseId") Long warehouseId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(s) FROM StockItem s WHERE s.quantity > 0 AND s.expDate IS NOT NULL AND s.expDate > :today AND s.expDate <= :cutoffDate AND (:warehouseId IS NULL OR s.binLocation.warehouse.id = :warehouseId)")
    long countExpiringSoonLots(@Param("warehouseId") Long warehouseId, @Param("today") LocalDate today, @Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT COUNT(s) FROM StockItem s WHERE s.quantity > 0 AND s.expDate IS NOT NULL AND s.expDate <= :today AND (:warehouseId IS NULL OR s.binLocation.warehouse.id = :warehouseId)")
    long countExpiredLots(@Param("warehouseId") Long warehouseId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(s) FROM StockItem s WHERE s.quantity > 0 AND s.locked = true AND (:warehouseId IS NULL OR s.binLocation.warehouse.id = :warehouseId)")
    long countLockedLots(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE (:warehouseId IS NULL OR s.binLocation.warehouse.id = :warehouseId)")
    BigDecimal sumQuantityInLots(@Param("warehouseId") Long warehouseId);
}
