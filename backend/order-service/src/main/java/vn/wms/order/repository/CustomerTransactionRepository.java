package vn.wms.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.order.entity.CustomerTransaction;

public interface CustomerTransactionRepository extends JpaRepository<CustomerTransaction, Long> {

    Page<CustomerTransaction> findByCustomerIdOrderByTransactionDateDesc(Long customerId, Pageable pageable);

    Page<CustomerTransaction> findByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);
}
