package vn.wms.common.security;

import java.util.Collections;
import java.util.Set;

/**
 * Thông tin định danh của người dùng hiện tại đang gửi yêu cầu tới hệ thống.
 * Được trích xuất từ các header do api-gateway chuyển tiếp (X-User-Name, X-User-Roles, X-User-Warehouse-Ids).
 */
public record CurrentUser(
        String username,
        Set<String> roles,
        Set<Long> warehouseIds
) {
    public CurrentUser {
        roles = (roles != null) ? Set.copyOf(roles) : Collections.emptySet();
        warehouseIds = (warehouseIds != null) ? Set.copyOf(warehouseIds) : Collections.emptySet();
    }

    /** Kiểm tra xem user có mang vai trò cụ thể nào không. */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
