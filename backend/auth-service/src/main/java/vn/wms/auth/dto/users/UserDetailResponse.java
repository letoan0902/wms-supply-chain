package vn.wms.auth.dto.users;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Chi tiết người dùng phục vụ API quản trị người dùng và phân quyền.
 */
public record UserDetailResponse(
        Long id,
        String username,
        String email,
        String fullName,
        boolean enabled,
        List<String> roles,
        Set<Long> warehouseIds,
        Long companyId,
        Instant createdAt,
        Instant updatedAt
) {
    public UserDetailResponse(Long id, String username, String email, String fullName, boolean enabled, List<String> roles, Set<Long> warehouseIds, Instant createdAt, Instant updatedAt) {
        this(id, username, email, fullName, enabled, roles, warehouseIds, null, createdAt, updatedAt);
    }
}
