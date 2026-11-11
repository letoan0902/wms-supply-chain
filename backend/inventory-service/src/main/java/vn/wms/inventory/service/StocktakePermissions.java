package vn.wms.inventory.service;

import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;

/**
 * Kiểm tra vai trò và phạm vi kho cho nghiệp vụ kiểm kê (inventory-service không bật method security nên
 * {@code @PreAuthorize} không có tác dụng). Theo ma trận phân quyền của SRS (mục 2.5):
 * <ul>
 *   <li>Lập kế hoạch, bắt đầu, kết thúc đếm, đếm lại, hủy, duyệt cấp quản lý: ADMIN hoặc WAREHOUSE_MANAGER phụ trách kho.</li>
 *   <li>Kiểm đếm: ADMIN, WAREHOUSE_MANAGER hoặc WAREHOUSE_STAFF của kho.</li>
 *   <li>Xem tồn hệ thống và chênh lệch: ADMIN, ACCOUNTANT, WAREHOUSE_MANAGER của kho (nhân viên kho không được xem, BR-12).</li>
 *   <li>Duyệt phiếu điều chỉnh cấp kế toán: ACCOUNTANT hoặc ADMIN.</li>
 * </ul>
 * Mọi API kiểm kê bắt buộc có người dùng đăng nhập (header do api-gateway chuyển tiếp).
 */
final class StocktakePermissions {

    private StocktakePermissions() {
    }

    static CurrentUser requireUser() {
        return UserContext.requireCurrentUser();
    }

    private static boolean hasWarehouse(CurrentUser user, Long warehouseId) {
        return warehouseId != null && user.warehouseIds().contains(warehouseId);
    }

    static boolean canManage(CurrentUser user, Long warehouseId) {
        return user.hasRole(Roles.ADMIN) || (user.hasRole(Roles.WAREHOUSE_MANAGER) && hasWarehouse(user, warehouseId));
    }

    static void requireManage(CurrentUser user, Long warehouseId) {
        if (!canManage(user, warehouseId)) {
            throw ApiException.forbidden("Chỉ Quản trị viên hoặc Quản lý kho phụ trách kho ID " + warehouseId
                    + " mới được thực hiện thao tác này với đợt kiểm kê");
        }
    }

    static void requireCount(CurrentUser user, Long warehouseId) {
        boolean warehouseRole = (user.hasRole(Roles.WAREHOUSE_MANAGER) || user.hasRole(Roles.WAREHOUSE_STAFF))
                && hasWarehouse(user, warehouseId);
        if (!user.hasRole(Roles.ADMIN) && !warehouseRole) {
            throw ApiException.forbidden("Chỉ nhân viên hoặc quản lý của kho ID " + warehouseId + " mới được kiểm đếm");
        }
    }

    static boolean canView(CurrentUser user, Long warehouseId) {
        if (user.hasRole(Roles.ADMIN) || user.hasRole(Roles.ACCOUNTANT)) {
            return true;
        }
        return (user.hasRole(Roles.WAREHOUSE_MANAGER) || user.hasRole(Roles.WAREHOUSE_STAFF)) && hasWarehouse(user, warehouseId);
    }

    static void requireView(CurrentUser user, Long warehouseId) {
        if (!canView(user, warehouseId)) {
            throw ApiException.forbidden("Bạn không có quyền xem đợt kiểm kê của kho ID: " + warehouseId);
        }
    }

    /** Có được xem tồn hệ thống, giữ chỗ và chênh lệch hay không. */
    static boolean canSeeSystemQuantities(CurrentUser user, Long warehouseId) {
        return user.hasRole(Roles.ADMIN) || user.hasRole(Roles.ACCOUNTANT)
                || (user.hasRole(Roles.WAREHOUSE_MANAGER) && hasWarehouse(user, warehouseId));
    }

    static void requireSeeSystemQuantities(CurrentUser user, Long warehouseId) {
        if (!canSeeSystemQuantities(user, warehouseId)) {
            throw ApiException.forbidden("Chỉ Quản trị viên, Kế toán hoặc Quản lý kho phụ trách mới được xem tồn hệ thống và chênh lệch kiểm kê");
        }
    }

    static void requireAccounting(CurrentUser user) {
        if (!user.hasRole(Roles.ADMIN) && !user.hasRole(Roles.ACCOUNTANT)) {
            throw ApiException.forbidden("Chỉ Kế toán hoặc Quản trị viên mới được duyệt phiếu điều chỉnh tồn kho");
        }
    }

    /** Vai trò được xem danh sách đợt kiểm kê (phạm vi kho lọc riêng). */
    static void requireListRole(CurrentUser user) {
        if (!user.hasRole(Roles.ADMIN) && !user.hasRole(Roles.ACCOUNTANT)
                && !user.hasRole(Roles.WAREHOUSE_MANAGER) && !user.hasRole(Roles.WAREHOUSE_STAFF)) {
            throw ApiException.forbidden("Bạn không có quyền xem đợt kiểm kê");
        }
    }
}
