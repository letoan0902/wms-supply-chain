package vn.wms.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.order.entity.PurchaseBill;
import vn.wms.order.entity.PurchaseBillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseBillRepository extends JpaRepository<PurchaseBill, Long> {

    Optional<PurchaseBill> findByBillNumber(String billNumber);

    boolean existsByBillNumber(String billNumber);

    Optional<PurchaseBill> findTopByBillNumberStartingWithOrderByBillNumberDesc(String prefix);

    /** Khóa bi quan (SELECT ... FOR UPDATE) hóa đơn khi ghi nhận thanh toán, chống hai lần trả đồng thời vượt số còn nợ. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PurchaseBill b WHERE b.id = :id")
    Optional<PurchaseBill> findByIdForUpdate(@Param("id") Long id);

    Optional<PurchaseBill> findByGoodsReceiptNoteId(Long grnId);

    List<PurchaseBill> findByPurchaseOrderIdOrderByIdDesc(Long poId);

    Page<PurchaseBill> findBySupplierIdOrderByIdDesc(Long supplierId, Pageable pageable);

    @Query("SELECT b FROM PurchaseBill b WHERE " +
           "(:keyword IS NULL OR LOWER(b.billNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(b.grnNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(b.poNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(b.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR b.status = :status) " +
           "AND (:supplierId IS NULL OR b.supplier.id = :supplierId) " +
           "AND (:poId IS NULL OR b.purchaseOrder.id = :poId) " +
           "AND (:fromDate IS NULL OR b.billDate >= :fromDate) " +
           "AND (:toDate IS NULL OR b.billDate <= :toDate)")
    Page<PurchaseBill> search(
            @Param("keyword") String keyword,
            @Param("status") PurchaseBillStatus status,
            @Param("supplierId") Long supplierId,
            @Param("poId") Long poId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    long countByStatus(PurchaseBillStatus status);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM PurchaseBill b WHERE b.status != 'CANCELLED'")
    BigDecimal sumTotalAmount();

    @Query("SELECT COALESCE(SUM(b.paidAmount), 0) FROM PurchaseBill b WHERE b.status != 'CANCELLED'")
    BigDecimal sumPaidAmount();

    @Query("SELECT COALESCE(SUM(b.remainingAmount), 0) FROM PurchaseBill b WHERE b.status != 'CANCELLED'")
    BigDecimal sumRemainingAmount();

    @Query("SELECT COALESCE(SUM(b.remainingAmount), 0) FROM PurchaseBill b WHERE b.supplier.id = :supplierId AND b.status != 'CANCELLED'")
    BigDecimal sumRemainingAmountBySupplierId(@Param("supplierId") Long supplierId);
}
