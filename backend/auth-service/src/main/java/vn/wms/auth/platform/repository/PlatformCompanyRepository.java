package vn.wms.auth.platform.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn doanh nghiệp xuyên doanh nghiệp cho quản trị nền tảng. auth-service không dùng TenantEntity (xem
 * docs/MULTI_TENANT.md mục 7) nên mọi truy vấn ở đây cố ý không lọc theo doanh nghiệp của người gọi; thay vào đó luôn
 * loại doanh nghiệp hệ thống {@code PLATFORM} một cách tường minh. Chỉ {@code PlatformService} (sau kiểm tra vai trò
 * {@code SUPER_ADMIN}) được dùng repository này.
 */
public interface PlatformCompanyRepository extends Repository<Company, Long> {

    /** Điều kiện loại doanh nghiệp hệ thống, dùng chung cho các câu truy vấn có bí danh {@code c}. */
    String NOT_PLATFORM = "c.code <> '" + Company.PLATFORM_CODE + "'";

    String SEARCH_WHERE = " WHERE " + NOT_PLATFORM
            + " AND (:status IS NULL OR c.status = :status)"
            + " AND (:pattern IS NULL OR LOWER(c.name) LIKE :pattern OR LOWER(c.code) LIKE :pattern"
            + "      OR LOWER(c.taxCode) LIKE :pattern OR LOWER(c.email) LIKE :pattern)"
            + " AND (:plan IS NULL OR EXISTS (SELECT 1 FROM Subscription s WHERE s.company = c AND s.plan.code = :plan))";

    /**
     * Trạng thái và thời điểm tạo của mọi doanh nghiệp khách hàng (mỗi dòng {@code [CompanyStatus, Instant]}), để đếm
     * theo trạng thái và theo tháng trong Java (chạy giống nhau trên MySQL và H2).
     */
    @Query("SELECT c.status, c.createdAt FROM Company c WHERE " + NOT_PLATFORM)
    List<Object[]> findStatusAndCreatedAtOfCustomers();

    /**
     * Tìm doanh nghiệp khách hàng theo trạng thái, gói của thuê bao và từ khóa ({@code pattern} đã viết thường và bọc
     * {@code %}, tìm trong tên, mã, mã số thuế, email). Tham số {@code null} nghĩa là không lọc.
     */
    @Query(value = "SELECT c FROM Company c" + SEARCH_WHERE,
            countQuery = "SELECT COUNT(c) FROM Company c" + SEARCH_WHERE)
    Page<Company> search(@Param("status") CompanyStatus status,
                         @Param("pattern") String pattern,
                         @Param("plan") String plan,
                         Pageable pageable);

    /** Doanh nghiệp khách hàng theo ID; doanh nghiệp {@code PLATFORM} coi như không tồn tại. */
    @Query("SELECT c FROM Company c WHERE c.id = :id AND " + NOT_PLATFORM)
    Optional<Company> findCustomerById(@Param("id") Long id);

    /** Như {@link #findCustomerById} nhưng khóa dòng (SELECT ... FOR UPDATE) trước khi đổi trạng thái. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Company c WHERE c.id = :id AND " + NOT_PLATFORM)
    Optional<Company> findCustomerByIdForUpdate(@Param("id") Long id);

    /** Doanh nghiệp theo danh sách ID (tra tên, mã cho danh sách thanh toán). */
    @Query("SELECT c FROM Company c WHERE c.id IN :ids")
    List<Company> findAllByIdIn(@Param("ids") Collection<Long> ids);
}
