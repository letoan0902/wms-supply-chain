package vn.wms.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.wms.order.entity.Supplier;

import java.math.BigDecimal;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    Optional<Supplier> findByTaxCode(String taxCode);

    boolean existsByTaxCode(String taxCode);

    boolean existsByTaxCodeAndIdNot(String taxCode, Long id);

    /** Khóa dòng bi quan (SELECT FOR UPDATE) để đồng bộ tính toán công nợ khi ghi nhận giao dịch đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Supplier s WHERE s.id = :id")
    Optional<Supplier> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT s FROM Supplier s " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(s.taxCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(COALESCE(s.contactName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "  AND (:active IS NULL OR s.active = :active)")
    Page<Supplier> search(@Param("keyword") String keyword,
                          @Param("active") Boolean active,
                          Pageable pageable);

    @Query("SELECT COALESCE(SUM(s.currentDebt), 0) FROM Supplier s WHERE s.active = true")
    BigDecimal sumTotalDebt();

    @Query("SELECT COUNT(s) FROM Supplier s WHERE s.active = true AND s.creditLimit > 0 AND s.currentDebt > s.creditLimit")
    long countOverLimitSuppliers();

    @Query("SELECT COUNT(s) FROM Supplier s WHERE s.active = true AND s.currentDebt > 0")
    long countSuppliersWithDebt();

    long countByActiveTrue();
}
