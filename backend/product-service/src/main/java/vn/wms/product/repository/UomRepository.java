package vn.wms.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.product.entity.Uom;

import java.util.Optional;

public interface UomRepository extends JpaRepository<Uom, Long> {

    Optional<Uom> findByCode(String code);
}
