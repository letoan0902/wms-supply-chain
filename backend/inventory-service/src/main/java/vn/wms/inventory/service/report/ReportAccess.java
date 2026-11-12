package vn.wms.inventory.service.report;

import org.springframework.stereotype.Component;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.repository.WarehouseRepository;

import java.util.Set;

/**
 * Phân quyền chung của các báo cáo kho (WMS-17.1, WMS-17.2), theo BR-13 (số liệu báo cáo tự lọc theo phạm vi kho):
 * <ul>
 *   <li>Được xem: ADMIN, ACCOUNTANT (mọi kho của doanh nghiệp), WAREHOUSE_MANAGER, WAREHOUSE_STAFF (chỉ các kho được
 *       gán). Vai trò khác nhận 403.</li>
 *   <li>inventory-service không bật method security nên kiểm tra tường minh ở đây, không dùng {@code @PreAuthorize}.</li>
 *   <li>Kho của doanh nghiệp khác hoặc không tồn tại: 404.</li>
 * </ul>
 */
@Component
public class ReportAccess {

    static final Set<String> REPORT_ROLES = Set.of(
            Roles.ADMIN, Roles.ACCOUNTANT, Roles.WAREHOUSE_MANAGER, Roles.WAREHOUSE_STAFF);

    private final WarehouseRepository warehouseRepository;

    public ReportAccess(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * Phạm vi dữ liệu của người xem.
     *
     * @param companyId    doanh nghiệp hiện tại
     * @param warehouseIds các kho được tổng hợp; {@code null} là mọi kho của doanh nghiệp
     */
    public record Scope(CurrentUser user, Long companyId, Set<Long> warehouseIds) {

        public boolean allows(Long warehouseId) {
            return warehouseIds == null || (warehouseId != null && warehouseIds.contains(warehouseId));
        }
    }

    /** Kiểm tra vai trò và xác định các kho được xem; {@code requestedWarehouseId} null là mọi kho trong phạm vi. */
    public Scope resolve(Long requestedWarehouseId) {
        CurrentUser user = UserContext.requireCurrentUser();
        if (REPORT_ROLES.stream().noneMatch(user::hasRole)) {
            throw ApiException.forbidden("Vai trò của tài khoản không được xem báo cáo kho");
        }
        Long companyId = TenantContext.requireCompanyId();
        Set<Long> allowed = UserContext.resolveAllowedWarehouses(user, requestedWarehouseId);
        if (requestedWarehouseId != null && !warehouseRepository.existsById(requestedWarehouseId)) {
            throw ApiException.notFound("Không tìm thấy kho ID: " + requestedWarehouseId);
        }
        return new Scope(user, companyId, allowed.isEmpty() ? null : Set.copyOf(allowed));
    }
}
