package vn.wms.auth.dto.users;

import java.util.List;
import java.util.Set;

/**
 * Thông tin hồ sơ chi tiết của người dùng.
 */
public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String fullName,
        boolean enabled,
        List<String> roles,
        Set<Long> warehouseIds
) {
}
