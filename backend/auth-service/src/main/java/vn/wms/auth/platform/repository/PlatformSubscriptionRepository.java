package vn.wms.auth.platform.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.subscription.entity.Subscription;

import java.util.Collection;
import java.util.List;

/**
 * Truy vấn thuê bao xuyên doanh nghiệp cho quản trị nền tảng, luôn loại doanh nghiệp hệ thống {@code PLATFORM}
 * (xem {@link PlatformCompanyRepository}).
 */
public interface PlatformSubscriptionRepository extends Repository<Subscription, Long> {

    /** Mọi thuê bao của doanh nghiệp khách hàng (số doanh nghiệp nhỏ, gói nạp kèm). */
    @Query("SELECT s FROM Subscription s WHERE s.company.code <> '" + Company.PLATFORM_CODE + "'")
    List<Subscription> findAllOfCustomers();

    /** Thuê bao của các doanh nghiệp trong danh sách ID (dựng một trang danh sách doanh nghiệp). */
    @Query("SELECT s FROM Subscription s WHERE s.company.id IN :companyIds")
    List<Subscription> findByCompanyIds(@Param("companyIds") Collection<Long> companyIds);
}
