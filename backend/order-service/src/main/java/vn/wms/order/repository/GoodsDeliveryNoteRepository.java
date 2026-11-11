package vn.wms.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.order.entity.GoodsDeliveryNote;
import vn.wms.order.entity.GoodsDeliveryStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoodsDeliveryNoteRepository extends JpaRepository<GoodsDeliveryNote, Long> {

    Optional<GoodsDeliveryNote> findByGdnNumber(String gdnNumber);

    boolean existsByGdnNumber(String gdnNumber);

    Optional<GoodsDeliveryNote> findTopByGdnNumberStartingWithOrderByGdnNumberDesc(String prefix);

    List<GoodsDeliveryNote> findBySalesOrderIdOrderByIdDesc(Long soId);

    @Query("SELECT g FROM GoodsDeliveryNote g WHERE " +
           "(:keyword IS NULL OR LOWER(g.gdnNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(g.soNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(COALESCE(g.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(COALESCE(g.recipientName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(COALESCE(g.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR g.status = :status) " +
           "AND (:customerId IS NULL OR g.customer.id = :customerId) " +
           "AND (:warehouseId IS NULL OR g.warehouseId = :warehouseId) " +
           "AND (:soId IS NULL OR g.salesOrder.id = :soId) " +
           "AND (:fromDeliveryDate IS NULL OR g.deliveryDate >= :fromDeliveryDate) " +
           "AND (:toDeliveryDate IS NULL OR g.deliveryDate <= :toDeliveryDate)")
    Page<GoodsDeliveryNote> search(
            @Param("keyword") String keyword,
            @Param("status") GoodsDeliveryStatus status,
            @Param("customerId") Long customerId,
            @Param("warehouseId") Long warehouseId,
            @Param("soId") Long soId,
            @Param("fromDeliveryDate") LocalDate fromDeliveryDate,
            @Param("toDeliveryDate") LocalDate toDeliveryDate,
            Pageable pageable
    );

    long countByStatus(GoodsDeliveryStatus status);

    @Query("SELECT COALESCE(SUM(g.totalQuantity), 0) FROM GoodsDeliveryNote g WHERE g.status = :status")
    BigDecimal sumTotalQuantityByStatus(@Param("status") GoodsDeliveryStatus status);

    @Query("SELECT COALESCE(SUM(g.totalAmount), 0) FROM GoodsDeliveryNote g WHERE g.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") GoodsDeliveryStatus status);
}
