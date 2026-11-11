package vn.wms.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.order.entity.GoodsReceiptNote;
import vn.wms.order.entity.GoodsReceiptStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoodsReceiptNoteRepository extends JpaRepository<GoodsReceiptNote, Long> {

    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "items"})
    Optional<GoodsReceiptNote> findByGrnNumber(String grnNumber);

    boolean existsByGrnNumber(String grnNumber);

    Optional<GoodsReceiptNote> findTopByGrnNumberStartingWithOrderByGrnNumberDesc(String prefix);

    @Override
    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "items"})
    Optional<GoodsReceiptNote> findById(Long id);

    @EntityGraph(attributePaths = {"supplier", "items"})
    List<GoodsReceiptNote> findByPurchaseOrderIdOrderByIdDesc(Long poId);

    @EntityGraph(attributePaths = {"supplier"})
    @Query("SELECT g FROM GoodsReceiptNote g " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(g.grnNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(g.poNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(g.supplierName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(g.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "  AND (:status IS NULL OR g.status = :status) " +
           "  AND (:warehouseId IS NULL OR g.warehouseId = :warehouseId) " +
           "  AND (:supplierId IS NULL OR g.supplier.id = :supplierId) " +
           "  AND (:poId IS NULL OR g.purchaseOrder.id = :poId) " +
           "  AND (:fromReceiptDate IS NULL OR g.receiptDate >= :fromReceiptDate) " +
           "  AND (:toReceiptDate IS NULL OR g.receiptDate <= :toReceiptDate)")
    Page<GoodsReceiptNote> search(
            @Param("keyword") String keyword,
            @Param("status") GoodsReceiptStatus status,
            @Param("warehouseId") Long warehouseId,
            @Param("supplierId") Long supplierId,
            @Param("poId") Long poId,
            @Param("fromReceiptDate") LocalDate fromReceiptDate,
            @Param("toReceiptDate") LocalDate toReceiptDate,
            Pageable pageable
    );

    long countByStatus(GoodsReceiptStatus status);

    @Query("SELECT COALESCE(SUM(g.totalAcceptedQuantity), 0) FROM GoodsReceiptNote g WHERE g.status = :status")
    BigDecimal sumTotalAcceptedQuantityByStatus(@Param("status") GoodsReceiptStatus status);

    @Query("SELECT COALESCE(SUM(g.totalRejectedQuantity), 0) FROM GoodsReceiptNote g WHERE g.status = :status")
    BigDecimal sumTotalRejectedQuantityByStatus(@Param("status") GoodsReceiptStatus status);

    @Query("SELECT COALESCE(SUM(g.totalAmount), 0) FROM GoodsReceiptNote g WHERE g.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") GoodsReceiptStatus status);
}
