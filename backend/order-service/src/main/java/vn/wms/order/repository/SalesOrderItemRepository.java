package vn.wms.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.SalesOrderItem;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Repository quản lý chi tiết dòng hàng đơn bán (so_items), phục vụ báo cáo tỷ trọng và thống kê bán chạy.
 */
public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {

    /**
     * Thống kê tổng sản lượng xuất bán theo từng mặt hàng trong khoảng thời gian.
     * Sắp xếp giảm dần theo tổng sản lượng để phục vụ biểu đồ Trending Items (WMS-16.2).
     *
     * Mỗi phần tử trong List là mảng Object:
     * - [0]: Long productId
     * - [1]: String skuCode
     * - [2]: String productName
     * - [3]: String uom
     * - [4]: BigDecimal totalQty (Tổng số lượng xuất)
     */
    @Query("SELECT i.productId, i.skuCode, i.productName, i.uom, COALESCE(SUM(i.quantity), 0) " +
           "FROM SalesOrderItem i " +
           "WHERE i.salesOrder.status IN :statuses " +
           "  AND i.salesOrder.orderDate >= :fromOrderDate " +
           "  AND i.salesOrder.orderDate <= :toOrderDate " +
           "  AND (:warehouseId IS NULL OR i.salesOrder.warehouseId = :warehouseId) " +
           "GROUP BY i.productId, i.skuCode, i.productName, i.uom " +
           "ORDER BY SUM(i.quantity) DESC")
    List<Object[]> findTopSoldProducts(
            @Param("fromOrderDate") LocalDate fromOrderDate,
            @Param("toOrderDate") LocalDate toOrderDate,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("warehouseId") Long warehouseId
    );
}
