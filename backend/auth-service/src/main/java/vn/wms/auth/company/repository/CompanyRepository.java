package vn.wms.auth.company.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByTaxCode(String taxCode);

    @Query("SELECT c FROM Company c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            " LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            " LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Company> searchCompanies(@Param("keyword") String keyword,
                                 @Param("status") CompanyStatus status,
                                 Pageable pageable);
}
