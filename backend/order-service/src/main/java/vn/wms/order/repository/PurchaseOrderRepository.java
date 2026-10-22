package vn.wms.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.order.entity.PurchaseOrder;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);
}
