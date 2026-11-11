package vn.wms.ai.extraction;

import vn.wms.common.exception.ApiException;
import vn.wms.common.security.CurrentUser;
import vn.wms.common.security.UserContext;

import java.util.Optional;

/**
 * Kiểm tra vai trò theo loại dữ liệu (docs/SAAS_SPEC.md, mục WMS-24.2): ai-service không bật method security nên
 * kiểm tra tường minh từ header X-User-Roles do api-gateway gắn.
 *
 * <p>Không có header người dùng (api-gateway tắt kiểm tra JWT ở máy phát triển) thì cho qua, cùng quy ước với
 * {@link UserContext#requireWarehouseWriteAccess}: gateway luôn gỡ header X-User-* do trình duyệt tự gửi, và trên máy
 * chủ (bật JWT) mọi request đã đăng nhập đều có header này.
 */
public final class ExtractionAccess {

    private ExtractionAccess() {
    }

    public static void requireRole(ExtractionDataType type) {
        requireRole(type, UserContext.getCurrentUser());
    }

    static void requireRole(ExtractionDataType type, Optional<CurrentUser> user) {
        if (user.isEmpty()) {
            return;
        }
        boolean allowed = type.allowedRoles().stream().anyMatch(user.get()::hasRole);
        if (!allowed) {
            throw ApiException.forbidden("Vai trò của bạn không được nhập dữ liệu " + type.label()
                    + " bằng AI. Vai trò được phép: " + String.join(", ", type.allowedRoles().stream().sorted()
                    .toList()) + ".");
        }
    }
}
