package vn.wms.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = {"supplier", "items"})
    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    boolean existsByPoNumber(String poNumber);

    Optional<PurchaseOrder> findTopByPoNumberStartingWithOrderByPoNumberDesc(String prefix);

    @Override
    @EntityGraph(attributePaths = {"supplier", "items"})
    Optional<PurchaseOrder> findById(Long id);

    /**
     * Khóa dòng bi quan (SELECT FOR UPDATE) để đồng bộ kiểm tra số lượng khi lập phiếu nhập kho đồng thời,
     * ngăn chặn tình trạng race condition dẫn đến nhập vượt số lượng đơn mua (WMS-10.2).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"supplier", "items"})
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"supplier"})
    Page<PurchaseOrder> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"supplier"})
    @Query("SELECT po FROM PurchaseOrder po " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(po.poNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(po.supplier.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(po.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "  AND (:supplierId IS NULL OR po.supplier.id = :supplierId) " +
           "  AND (:warehouseId IS NULL OR po.warehouseId = :warehouseId) " +
           "  AND (:status IS NULL OR po.status = :status) " +
           "  AND (:fromOrderDate IS NULL OR po.orderDate >= :fromOrderDate) " +
           "  AND (:toOrderDate IS NULL OR po.orderDate <= :toOrderDate)")
    Page<PurchaseOrder> search(
            @Param("keyword") String keyword,
            @Param("supplierId") Long supplierId,
            @Param("warehouseId") Long warehouseId,
            @Param("status") OrderStatus status,
            @Param("fromOrderDate") LocalDate fromOrderDate,
            @Param("toOrderDate") LocalDate toOrderDate,
            Pageable pageable
    );

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(po.totalAmount), 0) FROM PurchaseOrder po WHERE po.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COUNT(po) FROM PurchaseOrder po " +
           "WHERE po.status IN :statuses " +
           "  AND (:warehouseId IS NULL OR po.warehouseId = :warehouseId)")
    long countByStatusInAndWarehouseId(
            @Param("statuses") java.util.Collection<OrderStatus> statuses,
            @Param("warehouseId") Long warehouseId
    );

    @Query("SELECT COUNT(po) FROM PurchaseOrder po " +
           "WHERE po.status IN :statuses " +
           "  AND po.expectedDate = :expectedDate " +
           "  AND (:warehouseId IS NULL OR po.warehouseId = :warehouseId)")
    long countArrivingOrders(
            @Param("statuses") java.util.Collection<OrderStatus> statuses,
            @Param("expectedDate") LocalDate expectedDate,
            @Param("warehouseId") Long warehouseId
    );
}
