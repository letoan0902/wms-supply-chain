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

    /** Số kho đang hoạt động của doanh nghiệp hiện tại (Hibernate tự lọc theo company_id), dùng cho giới hạn gói. */
    long countByActiveTrue();

    List<Warehouse> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByCodeAsc(String name, String code);
}
