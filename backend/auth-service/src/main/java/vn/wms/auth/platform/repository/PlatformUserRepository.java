package vn.wms.auth.platform.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.entity.User;
import vn.wms.common.constant.Roles;

import java.util.Collection;
import java.util.List;

/**
 * Truy vấn tài khoản xuyên doanh nghiệp cho quản trị nền tảng. Không bao giờ đếm hay trả tài khoản của doanh nghiệp
 * hệ thống {@code PLATFORM} (xem {@link PlatformCompanyRepository}).
 */
public interface PlatformUserRepository extends Repository<User, Long> {

    /** Số tài khoản của mọi doanh nghiệp khách hàng. */
    @Query("SELECT COUNT(u) FROM User u WHERE u.company.code <> '" + Company.PLATFORM_CODE + "'")
    long countCustomerUsers();

    /** Số tài khoản đang bật của mọi doanh nghiệp khách hàng. */
    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true AND u.company.code <> '" + Company.PLATFORM_CODE + "'")
    long countEnabledCustomerUsers();

    /** Số tài khoản theo doanh nghiệp, mỗi dòng {@code [companyId, count]}. */
    @Query("SELECT u.company.id, COUNT(u) FROM User u WHERE u.company.id IN :companyIds GROUP BY u.company.id")
    List<Object[]> countByCompanyIds(@Param("companyIds") Collection<Long> companyIds);

    /** Tài khoản có vai trò ADMIN của một doanh nghiệp, theo ID tăng dần. */
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE u.company.id = :companyId AND r.name = '"
            + Roles.ADMIN + "' ORDER BY u.id")
    List<User> findAdmins(@Param("companyId") Long companyId);
}
