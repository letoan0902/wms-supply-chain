package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.inventory.entity.BinLocation;

import java.util.List;
import java.util.Optional;

public interface BinLocationRepository extends JpaRepository<BinLocation, Long> {

    Optional<BinLocation> findByCode(String code);

    List<BinLocation> findByWarehouseId(Long warehouseId);
}
