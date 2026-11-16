package vn.wms.auth.company.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.wms.auth.company.entity.Company;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByTaxCode(String taxCode);

    /** Id các doanh nghiệp mang mã số thuế này (đăng ký lại khi bản đăng ký cũ bỏ dở). */
    @Query("SELECT c.id FROM Company c WHERE c.taxCode = :taxCode")
    List<Long> findIdsByTaxCode(@Param("taxCode") String taxCode);

    /** Đọc doanh nghiệp và khóa dòng (SELECT ... FOR UPDATE) trước khi gỡ một bản đăng ký bỏ dở. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Company c WHERE c.id = :id")
    Optional<Company> findByIdForUpdate(@Param("id") Long id);
}
