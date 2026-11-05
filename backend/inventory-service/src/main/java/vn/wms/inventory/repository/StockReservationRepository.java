package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.inventory.entity.StockReservation;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, String status);

    boolean existsByMessageId(String messageId);

    List<StockReservation> findByOrderIdAndStatus(Long orderId, String status);

    List<StockReservation> findByProductIdAndStatus(Long productId, String status);
}
