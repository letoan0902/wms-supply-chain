package vn.wms.inventory.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.StockMovementType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Truy vấn tổng hợp chỉ đọc cho các biểu đồ kho trên dashboard (WMS-16.4). Tách khỏi {@code StockItemRepository},
 * {@code StockMovementRepository} dùng chung để không đụng tới các truy vấn nghiệp vụ.
 *
 * <p>Đều là JPQL nên Hibernate tự thêm điều kiện doanh nghiệp (@TenantId); không đổi sang native SQL.
 * {@code allWarehouses = true} thì bỏ qua {@code warehouseIds} (vẫn phải truyền danh sách không rỗng vì mệnh đề IN
 * không nhận danh sách rỗng).
 */
public interface InventoryDashboardQueryRepository extends Repository<StockMovement, Long> {

    /**
     * Tồn thực tế (mọi dòng tồn còn hàng, kể cả phần đã giữ chỗ, lô bị khóa hoặc hết hạn) theo sản phẩm.
     * Mỗi phần tử: [0] Long productId, [1] BigDecimal số lượng.
     */
    @Query("SELECT s.productId, COALESCE(SUM(s.quantity), 0) FROM StockItem s " +
            "WHERE s.quantity > 0 " +
            "  AND (:allWarehouses = true OR s.binLocation.warehouse.id IN :warehouseIds) " +
            "GROUP BY s.productId")
    List<Object[]> sumOnHandByProduct(@Param("allWarehouses") boolean allWarehouses,
                                      @Param("warehouseIds") Collection<Long> warehouseIds);

    /**
     * Các chứng từ kho gần nhất, gom các dòng sổ kho của cùng một chứng từ, cùng loại biến động, cùng kho; mới nhất
     * trước, số dòng lấy theo {@code pageable}. Mỗi phần tử:
     * [0] String documentType, [1] Long documentId, [2] String documentNumber, [3] StockMovementType movementType,
     * [4] Long warehouseId, [5] Instant thời điểm phát sinh gần nhất, [6] BigDecimal tổng nhập, [7] BigDecimal tổng xuất,
     * [8] Long số dòng sổ, [9] Long số mặt hàng, [10] String người thực hiện, [11] Long kho bên kia (điều chuyển).
     */
    @Query("SELECT m.documentType, m.documentId, m.documentNumber, m.movementType, m.warehouseId, " +
            "       MAX(m.occurredAt), COALESCE(SUM(m.quantityIn), 0), COALESCE(SUM(m.quantityOut), 0), " +
            "       COUNT(m), COUNT(DISTINCT m.productId), MAX(m.performedBy), MAX(m.counterpartWarehouseId) " +
            "FROM StockMovement m " +
            "WHERE m.movementType <> :excludedType " +
            "  AND m.occurredAt >= :from " +
            "  AND (:allWarehouses = true OR m.warehouseId IN :warehouseIds) " +
            "GROUP BY m.documentType, m.documentId, m.documentNumber, m.movementType, m.warehouseId " +
            "ORDER BY MAX(m.occurredAt) DESC")
    List<Object[]> findRecentDocuments(@Param("excludedType") StockMovementType excludedType,
                                       @Param("from") Instant from,
                                       @Param("allWarehouses") boolean allWarehouses,
                                       @Param("warehouseIds") Collection<Long> warehouseIds,
                                       Pageable pageable);
}
