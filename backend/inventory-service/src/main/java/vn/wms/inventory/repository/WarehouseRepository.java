package vn.wms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.inventory.entity.Warehouse;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Warehouse> findAllByOrderByCodeAsc();

    List<Warehouse> findByIdInOrderByCodeAsc(Collection<Long> ids);

    List<Warehouse> findByActiveTrueOrderByCodeAsc();

    List<Warehouse> findByIdInAndActiveTrueOrderByCodeAsc(Collection<Long> ids);

    List<Warehouse> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByCodeAsc(String name, String code);
}
