package vn.wms.auth.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Phản hồi sau khi tạo mới tài khoản thành công.
 */
public record RegisterResponse(
        Long id,
        String username,
        String email,
        String fullName,
        boolean enabled,
        Set<String> roles,
        Instant createdAt
) {
}
