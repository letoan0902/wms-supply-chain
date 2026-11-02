package vn.wms.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.order.entity.SupplierTransaction;

public interface SupplierTransactionRepository extends JpaRepository<SupplierTransaction, Long> {

    Page<SupplierTransaction> findBySupplierIdOrderByTransactionDateDesc(Long supplierId, Pageable pageable);

    Page<SupplierTransaction> findBySupplierIdOrderByIdDesc(Long supplierId, Pageable pageable);
}
