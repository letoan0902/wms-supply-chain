package vn.wms.common.tenant.sample;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ứng dụng tối thiểu chỉ dùng trong test của module common: một entity kế thừa TenantEntity, một repository và
 * một controller, đủ để kiểm tra cơ chế tách dữ liệu theo doanh nghiệp chạy thật trên JPA (H2) và Spring MVC.
 */
@SpringBootApplication
public class TenantTestApplication {
}
