package vn.wms.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.InventoryLot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

    Optional<InventoryLot> findByLotNumberAndBinLocationIdAndProductId(
            String lotNumber, Long binLocationId, Long productId);

    Optional<InventoryLot> findByCompanyIdAndLotNumberAndBinLocationIdAndProductId(
            Long companyId, String lotNumber, Long binLocationId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM InventoryLot l WHERE l.id = :id")
    Optional<InventoryLot> findByIdWithLock(@Param("id") Long id);

    /**
     * Xuất kho theo thuật toán FEFO (First-Expired, First-Out):
     * Khóa bi quan và sắp xếp lô theo hạn dùng tăng dần (expDate ASC), không có hạn dùng thì xếp theo FIFO (id ASC).
     * Tự động loại trừ các lô đang bị khóa hoặc đã hết hạn sử dụng (so với ngày truyền vào từ Java).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM InventoryLot l " +
           "WHERE l.productId = :productId " +
           "  AND l.binLocation.warehouse.id = :warehouseId " +
           "  AND l.quantity > 0 " +
           "  AND l.locked = false " +
           "  AND (l.expDate IS NULL OR l.expDate > :today) " +
           "ORDER BY CASE WHEN l.expDate IS NULL THEN 1 ELSE 0 END ASC, l.expDate ASC, l.id ASC")
    List<InventoryLot> findAvailableLotsByFefo(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId,
            @Param("today") LocalDate today);

    /**
     * Tìm tất cả các lô hàng đã hết hạn sử dụng (expDate <= today) nhưng chưa bị khóa.
     */
    @Query("SELECT l FROM InventoryLot l " +
           "WHERE l.expDate IS NOT NULL " +
           "  AND l.expDate <= :today " +
           "  AND l.locked = false " +
           "  AND l.quantity > 0")
    List<InventoryLot> findExpiredUnlockedLots(@Param("today") LocalDate today);

    /**
     * Tìm các lô hàng cận hạn sử dụng trong N ngày tới.
     * Sử dụng JOIN FETCH cho binLocation và warehouse để chống lỗi N+1 queries.
     */
    @Query("SELECT l FROM InventoryLot l " +
           "LEFT JOIN FETCH l.binLocation b " +
           "LEFT JOIN FETCH b.warehouse w " +
           "WHERE l.expDate IS NOT NULL " +
           "  AND l.expDate > :today " +
           "  AND l.expDate <= :cutoffDate " +
           "  AND l.locked = false " +
           "  AND l.quantity > 0 " +
           "ORDER BY l.expDate ASC")
    List<InventoryLot> findExpiringSoonLots(
            @Param("today") LocalDate today,
            @Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Tìm kiếm và phân trang danh sách các lô hàng (inventory_lots).
     * Sử dụng JOIN FETCH cho binLocation và warehouse để nạp dữ liệu một lần, loại bỏ hoàn toàn N+1 queries.
     */
    @Query(value = "SELECT l FROM InventoryLot l " +
           "LEFT JOIN FETCH l.binLocation b " +
           "LEFT JOIN FETCH b.warehouse w " +
           "WHERE (:warehouseId IS NULL OR b.warehouse.id = :warehouseId) " +
           "  AND (:productId IS NULL OR l.productId = :productId) " +
           "  AND (:lotNumber IS NULL OR :lotNumber = '' OR LOWER(l.lotNumber) LIKE LOWER(CONCAT('%', :lotNumber, '%'))) " +
           "  AND (:locked IS NULL OR l.locked = :locked) " +
           "  AND (:hasStock IS NULL OR (:hasStock = true AND l.quantity > 0) OR (:hasStock = false AND l.quantity = 0)) " +
           "  AND (:isExpired IS NULL OR (:isExpired = true AND l.expDate IS NOT NULL AND l.expDate <= :today) " +
           "                          OR (:isExpired = false AND (l.expDate IS NULL OR l.expDate > :today))) " +
           "  AND (:isExpiringSoon IS NULL OR (:isExpiringSoon = true AND l.expDate IS NOT NULL AND l.expDate > :today AND l.expDate <= :cutoffDate))",
           countQuery = "SELECT COUNT(l) FROM InventoryLot l " +
           "WHERE (:warehouseId IS NULL OR l.binLocation.warehouse.id = :warehouseId) " +
           "  AND (:productId IS NULL OR l.productId = :productId) " +
           "  AND (:lotNumber IS NULL OR :lotNumber = '' OR LOWER(l.lotNumber) LIKE LOWER(CONCAT('%', :lotNumber, '%'))) " +
           "  AND (:locked IS NULL OR l.locked = :locked) " +
           "  AND (:hasStock IS NULL OR (:hasStock = true AND l.quantity > 0) OR (:hasStock = false AND l.quantity = 0)) " +
           "  AND (:isExpired IS NULL OR (:isExpired = true AND l.expDate IS NOT NULL AND l.expDate <= :today) " +
           "                          OR (:isExpired = false AND (l.expDate IS NULL OR l.expDate > :today))) " +
           "  AND (:isExpiringSoon IS NULL OR (:isExpiringSoon = true AND l.expDate IS NOT NULL AND l.expDate > :today AND l.expDate <= :cutoffDate))")
    Page<InventoryLot> searchLots(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("lotNumber") String lotNumber,
            @Param("locked") Boolean locked,
            @Param("hasStock") Boolean hasStock,
            @Param("isExpired") Boolean isExpired,
            @Param("isExpiringSoon") Boolean isExpiringSoon,
            @Param("today") LocalDate today,
            @Param("cutoffDate") LocalDate cutoffDate,
            Pageable pageable);

    @Query("SELECT COUNT(l) FROM InventoryLot l WHERE (:warehouseId IS NULL OR l.binLocation.warehouse.id = :warehouseId)")
    long countTotalLots(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(l) FROM InventoryLot l WHERE l.quantity > 0 AND l.locked = false AND (l.expDate IS NULL OR l.expDate > :today) AND (:warehouseId IS NULL OR l.binLocation.warehouse.id = :warehouseId)")
    long countActiveLots(@Param("warehouseId") Long warehouseId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(l) FROM InventoryLot l WHERE l.quantity > 0 AND l.expDate IS NOT NULL AND l.expDate > :today AND l.expDate <= :cutoffDate AND (:warehouseId IS NULL OR l.binLocation.warehouse.id = :warehouseId)")
    long countExpiringSoonLots(@Param("warehouseId") Long warehouseId, @Param("today") LocalDate today, @Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT COUNT(l) FROM InventoryLot l WHERE l.quantity > 0 AND l.expDate IS NOT NULL AND l.expDate <= :today AND (:warehouseId IS NULL OR l.binLocation.warehouse.id = :warehouseId)")
    long countExpiredLots(@Param("warehouseId") Long warehouseId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(l) FROM InventoryLot l WHERE l.quantity > 0 AND l.locked = true AND (:warehouseId IS NULL OR l.binLocation.warehouse.id = :warehouseId)")
    long countLockedLots(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(l.quantity), 0) FROM InventoryLot l WHERE (:warehouseId IS NULL OR l.binLocation.warehouse.id = :warehouseId)")
    BigDecimal sumQuantityInLots(@Param("warehouseId") Long warehouseId);
}
