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

    /**
     * Khóa dòng bi quan (SELECT FOR UPDATE) đơn bán khi lập phiếu xuất kho: hai phiếu xuất đồng thời cho cùng
     * một đơn phải xếp hàng, phiếu sau đọc số lượng đã xuất mới nhất nên không thể xuất vượt số lượng đặt (WMS-12.1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customer", "items"})
    @Query("SELECT so FROM SalesOrder so WHERE so.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    /*
     * Hai truy vấn phân trang dưới đây chỉ nạp kèm khách hàng, KHÔNG nạp kèm danh sách dòng hàng (WMS-19.3): nạp
     * kèm một collection khi phân trang khiến Hibernate đọc toàn bộ đơn khớp điều kiện rồi mới cắt trang trong bộ nhớ
     * (cảnh báo HHH90003004). Dòng hàng của cả trang được nạp sau bằng một câu IN nhờ hibernate.default_batch_fetch_size.
     */
    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<SalesOrder> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
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
