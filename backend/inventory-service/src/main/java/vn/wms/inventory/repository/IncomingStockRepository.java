package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.inventory.entity.IncomingStock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncomingStockRepository extends JpaRepository<IncomingStock, Long> {

    Optional<IncomingStock> findByOrderIdAndProductId(Long orderId, Long productId);

    List<IncomingStock> findByWarehouseIdAndStatus(Long warehouseId, String status);

    List<IncomingStock> findByWarehouseIdAndProductIdAndStatus(Long warehouseId, Long productId, String status);

    List<IncomingStock> findByOrderId(Long orderId);

    boolean existsByOrderIdAndProductId(Long orderId, Long productId);

    /**
     * Tính tổng số lượng hàng đang về (INCOMING) của một SKU tại một kho cụ thể.
     */
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM IncomingStock i " +
           "WHERE i.warehouse.id = :warehouseId AND i.productId = :productId AND i.status = 'INCOMING'")
    BigDecimal calculateIncomingQuantity(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    /**
     * Tính tổng số lượng hàng đang về (INCOMING) của một SKU trên toàn hệ thống kho.
     */
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM IncomingStock i " +
           "WHERE i.productId = :productId AND i.status = 'INCOMING'")
    BigDecimal calculateTotalIncomingQuantityByProduct(@Param("productId") Long productId);

    /**
     * Tính tổng số lượng hàng đang về (INCOMING) của toàn bộ các sản phẩm tại một kho.
     */
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM IncomingStock i " +
           "WHERE i.warehouse.id = :warehouseId AND i.status = 'INCOMING'")
    BigDecimal calculateTotalIncomingQuantityByWarehouse(@Param("warehouseId") Long warehouseId);
}

