package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.inventory.entity.StockReservation;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, String status);

    boolean existsByMessageId(String messageId);

    /** Đơn đã từng có bản ghi giữ chỗ (mọi trạng thái), dùng để listener order.created không giữ lại lần hai (WMS-20.4). */
    boolean existsByOrderId(Long orderId);

    List<StockReservation> findByOrderIdAndStatus(Long orderId, String status);

    List<StockReservation> findByProductIdAndStatus(Long productId, String status);

    List<StockReservation> findByStockItemIdAndStatus(Long stockItemId, String status);

    List<StockReservation> findByOrderIdAndProductIdAndStatus(Long orderId, Long productId, String status);
}
