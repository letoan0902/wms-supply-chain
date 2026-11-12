package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockAdjustment;

import java.math.BigDecimal;
import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long>,
        JpaSpecificationExecutor<StockAdjustment> {

    Optional<StockAdjustment> findBySessionId(Long sessionId);

    boolean existsByCode(String code);

    @Query("SELECT COUNT(a) FROM StockAdjustment a WHERE a.code LIKE CONCAT(:prefix, '%')")
    long countByCodePrefix(@Param("prefix") String prefix);

    /**
     * Tổng tồn thực tế (mọi dòng, kể cả lô bị khóa hoặc hết hạn) của một sản phẩm trong kho, dùng làm tồn trước
     * và sau của sự kiện inventory.updated khi điều chỉnh kiểm kê. Đặt ở đây để không sửa StockItemRepository dùng chung.
     */
    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockItem s WHERE s.productId = :productId AND s.binLocation.warehouse.id = :warehouseId")
    BigDecimal sumOnHandByProductAndWarehouse(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);
}
