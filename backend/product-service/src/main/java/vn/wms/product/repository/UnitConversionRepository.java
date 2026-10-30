package vn.wms.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.product.entity.UnitConversion;

import java.util.List;
import java.util.Optional;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {

    List<UnitConversion> findByProductId(Long productId);

    boolean existsByProductIdAndFromUomIdAndToUomId(Long productId, Long fromUomId, Long toUomId);

    boolean existsByProductIdAndFromUomIdAndToUomIdAndIdNot(Long productId, Long fromUomId, Long toUomId, Long id);

    Optional<UnitConversion> findByProductIdAndFromUomIdAndToUomId(Long productId, Long fromUomId, Long toUomId);

    Optional<UnitConversion> findByProductIdAndBarcode(Long productId, String barcode);

    void deleteByProductId(Long productId);
}
