package vn.wms.auth.internal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.wms.auth.entity.User;

import java.util.List;

/**
 * Truy vấn người nhận cảnh báo tồn kho, tách khỏi {@code UserRepository} để không đụng code của task khác.
 * auth-service không dùng TenantEntity nên điều kiện doanh nghiệp được viết tường minh.
 */
public interface AlertRecipientRepository extends Repository<User, Long> {

    /**
     * Tài khoản đang hoạt động của một doanh nghiệp có vai trò ADMIN, hoặc WAREHOUSE_MANAGER được phân công kho
     * {@code warehouseId} (bỏ qua nhánh này khi {@code warehouseId} là null). Nạp sẵn vai trò.
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.roles " +
           "WHERE u.company.id = :companyId AND u.enabled = true AND EXISTS (" +
           "  SELECT 1 FROM User u2 JOIN u2.roles r LEFT JOIN u2.warehouseIds w " +
           "  WHERE u2.id = u.id AND (r.name = 'ADMIN' " +
           "    OR (:warehouseId IS NOT NULL AND r.name = 'WAREHOUSE_MANAGER' AND w = :warehouseId)))")
    List<User> findAlertRecipients(@Param("companyId") Long companyId, @Param("warehouseId") Long warehouseId);
}
