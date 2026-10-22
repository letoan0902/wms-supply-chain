package vn.wms.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.order.entity.Supplier;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCode(String code);
}
