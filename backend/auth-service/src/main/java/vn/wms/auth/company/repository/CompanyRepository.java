package vn.wms.auth.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.wms.auth.company.entity.Company;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByTaxCode(String taxCode);
}
