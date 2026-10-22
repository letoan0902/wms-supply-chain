package vn.wms.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.order.entity.SalesOrder;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findBySoNumber(String soNumber);
}
