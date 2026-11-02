package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.inventory.entity.BinLocation;

import java.util.List;
import java.util.Optional;

public interface BinLocationRepository extends JpaRepository<BinLocation, Long> {

    List<BinLocation> findByWarehouseIdOrderByZoneAscRackAscBinAsc(Long warehouseId);

    List<BinLocation> findByWarehouseIdAndZoneOrderByRackAscBinAsc(Long warehouseId, String zone);

    Optional<BinLocation> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByWarehouseIdAndZoneAndRackAndBin(Long warehouseId, String zone, String rack, String bin);

    boolean existsByWarehouseId(Long warehouseId);

    long countByWarehouseId(Long warehouseId);

    long countByWarehouseIdAndActive(Long warehouseId, boolean active);

    long countByWarehouseIdAndStatus(Long warehouseId, String status);

    long countByStatus(String status);

    @Query("SELECT DISTINCT b.zone FROM BinLocation b WHERE b.warehouse.id = :warehouseId ORDER BY b.zone ASC")
    List<String> findDistinctZonesByWarehouseId(@Param("warehouseId") Long warehouseId);
}
