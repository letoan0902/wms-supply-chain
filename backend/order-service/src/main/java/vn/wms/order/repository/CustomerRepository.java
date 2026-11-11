package vn.wms.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.CustomerType;

import java.math.BigDecimal;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    Optional<Customer> findByTaxCode(String taxCode);

    boolean existsByTaxCode(String taxCode);

    boolean existsByTaxCodeAndIdNot(String taxCode, Long id);

    /** Khóa dòng bi quan (SELECT FOR UPDATE) để đồng bộ tính toán công nợ khi ghi nhận giao dịch đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT c FROM Customer c " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(c.taxCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(c.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "  AND (:type IS NULL OR c.customerType = :type) " +
           "  AND (:active IS NULL OR c.active = :active) " +
           "  AND (:debtLocked IS NULL OR c.debtLocked = :debtLocked) " +
           "  AND (:overLimit IS NULL OR (:overLimit = true AND c.creditLimit > 0 AND c.currentDebt > c.creditLimit) " +
           "                          OR (:overLimit = false AND (c.creditLimit <= 0 OR c.currentDebt <= c.creditLimit)))")
    Page<Customer> search(@Param("keyword") String keyword,
                          @Param("type") CustomerType type,
                          @Param("active") Boolean active,
                          @Param("debtLocked") Boolean debtLocked,
                          @Param("overLimit") Boolean overLimit,
                          Pageable pageable);

    @Query("SELECT COALESCE(SUM(c.currentDebt), 0) FROM Customer c WHERE c.active = true")
    BigDecimal sumTotalDebt();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.active = true AND c.creditLimit > 0 AND c.currentDebt > c.creditLimit")
    long countOverLimitCustomers();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.active = true AND c.currentDebt > 0")
    long countCustomersWithDebt();

    long countByActiveTrue();

    long countByDebtLockedTrue();
}
