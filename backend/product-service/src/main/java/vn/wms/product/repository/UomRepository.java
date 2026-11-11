package vn.wms.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.product.entity.Uom;

import java.util.Optional;

public interface UomRepository extends JpaRepository<Uom, Long> {

    Optional<Uom> findByCode(String code);

    boolean existsByCode(String code);

    /** Tìm đơn vị tính theo tên, không phân biệt hoa thường (nhập hàng loạt dùng tên đơn vị tính). */
    Optional<Uom> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    /** Tìm đơn vị tính theo mã, không phân biệt hoa thường. */
    Optional<Uom> findFirstByCodeIgnoreCaseOrderByIdAsc(String code);
}
