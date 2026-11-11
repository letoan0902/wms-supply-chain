package vn.wms.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.SalesOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<SalesOrder> findBySoNumber(String soNumber);

    boolean existsBySoNumber(String soNumber);

    Optional<SalesOrder> findTopBySoNumberStartingWithOrderBySoNumberDesc(String prefix);

    @Override
    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<SalesOrder> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"customer", "items"})
    Page<SalesOrder> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "items"})
    @Query("SELECT so FROM SalesOrder so " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(so.soNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(so.customer.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(so.customer.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(so.deliveryAddress, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(so.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "  AND (:customerId IS NULL OR so.customer.id = :customerId) " +
           "  AND (:warehouseId IS NULL OR so.warehouseId = :warehouseId) " +
           "  AND (:status IS NULL OR so.status = :status) " +
           "  AND (:fromOrderDate IS NULL OR so.orderDate >= :fromOrderDate) " +
           "  AND (:toOrderDate IS NULL OR so.orderDate <= :toOrderDate)")
    Page<SalesOrder> search(
            @Param("keyword") String keyword,
            @Param("customerId") Long customerId,
            @Param("warehouseId") Long warehouseId,
            @Param("status") OrderStatus status,
            @Param("fromOrderDate") LocalDate fromOrderDate,
            @Param("toOrderDate") LocalDate toOrderDate,
            Pageable pageable
    );

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(so.totalAmount), 0) FROM SalesOrder so WHERE so.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(so.totalAmount), 0) FROM SalesOrder so WHERE so.status IN :statuses")
    BigDecimal sumTotalAmountByStatuses(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(so.totalAmount), 0) FROM SalesOrder so " +
           "WHERE so.status IN :statuses " +
           "  AND so.orderDate >= :fromOrderDate AND so.orderDate <= :toOrderDate " +
           "  AND (:warehouseId IS NULL OR so.warehouseId = :warehouseId)")
    BigDecimal sumRevenueBetweenDates(
            @Param("fromOrderDate") LocalDate fromOrderDate,
            @Param("toOrderDate") LocalDate toOrderDate,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("warehouseId") Long warehouseId
    );

    @Query("SELECT COUNT(so) FROM SalesOrder so " +
           "WHERE so.status IN :statuses " +
           "  AND (:warehouseId IS NULL OR so.warehouseId = :warehouseId)")
    long countByStatusInAndWarehouseId(
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("warehouseId") Long warehouseId
    );

    @Query("SELECT COUNT(so) FROM SalesOrder so " +
           "WHERE so.status IN :statuses " +
           "  AND so.expectedDate <= :maxExpectedDate " +
           "  AND (:warehouseId IS NULL OR so.warehouseId = :warehouseId)")
    long countUrgentOrders(
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("maxExpectedDate") LocalDate maxExpectedDate,
            @Param("warehouseId") Long warehouseId
    );
}
