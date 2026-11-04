package vn.wms.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;

import java.util.*;

/**
 * Tiện ích đọc thông tin người dùng từ HTTP Request và kiểm tra quyền truy cập kho.
 */
public final class UserContext {

    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_WAREHOUSE_IDS = "X-User-Warehouse-Ids";

    private UserContext() {
        // Lớp tiện ích không khởi tạo
    }

    /**
     * Lấy thông tin người dùng hiện tại từ RequestContext của Spring.
     * Trả về Optional.empty() nếu không nằm trong luồng HTTP hoặc không có header người dùng.
     */
    public static Optional<CurrentUser> getCurrentUser() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return getCurrentUser(servletAttributes.getRequest());
        }
        return Optional.empty();
    }

    /**
     * Lấy thông tin người dùng từ một HttpServletRequest cụ thể.
     */
    public static Optional<CurrentUser> getCurrentUser(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        String username = request.getHeader(HEADER_USER_NAME);
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        String rolesHeader = request.getHeader(HEADER_USER_ROLES);
        Set<String> roles = parseRoles(rolesHeader);

        String warehouseIdsHeader = request.getHeader(HEADER_USER_WAREHOUSE_IDS);
        Set<Long> warehouseIds = parseWarehouseIds(warehouseIdsHeader);

        return Optional.of(new CurrentUser(username.trim(), roles, warehouseIds));
    }

    /**
     * Yêu cầu bắt buộc phải có người dùng đăng nhập.
     * Ném lỗi 401 Unauthorized nếu không tìm thấy thông tin người dùng.
     */
    public static CurrentUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> ApiException.unauthorized("Yêu cầu thông tin xác thực người dùng"));
    }

    /**
     * Kiểm tra người dùng có quyền xem toàn bộ kho (Global Scope) hay không.
     * Theo tài liệu đặc tả: ADMIN và ACCOUNTANT được phép truy cập tất cả các kho.
     */
    public static boolean isGlobalScope(CurrentUser user) {
        if (user == null) {
            return false;
        }
        return user.hasRole(Roles.ADMIN) || user.hasRole(Roles.ACCOUNTANT);
    }

    /**
     * Xác định và kiểm tra tính hợp lệ của danh sách kho cần truy vấn dữ liệu:
     * - Nếu là Admin / Kế toán (Global Scope):
     *     + Có truyền requestedWarehouseId -> Trả về tập chứa đúng ID kho đó.
     *     + Không truyền requestedWarehouseId -> Trả về tập rỗng (nghĩa là không lọc theo kho, lấy tất cả).
     * - Nếu là Quản lý / Nhân viên kho (Warehouse Scope):
     *     + Tài khoản chưa được gán kho -> Ném lỗi 403 Forbidden.
     *     + Có truyền requestedWarehouseId nhưng KHÔNG nằm trong kho được gán -> Ném lỗi 403 Forbidden (Chống IDOR).
     *     + Có truyền requestedWarehouseId và hợp lệ -> Trả về tập chứa đúng ID kho đó.
     *     + Không truyền requestedWarehouseId -> Trả về toàn bộ danh sách kho user được phụ trách.
     */
    public static Set<Long> resolveAllowedWarehouses(CurrentUser user, Long requestedWarehouseId) {
        if (user == null) {
            throw ApiException.unauthorized("Chưa đăng nhập hoặc thiếu thông tin người dùng");
        }

        // 1. Nhóm toàn quyền (ADMIN, ACCOUNTANT)
        if (isGlobalScope(user)) {
            return requestedWarehouseId != null ? Set.of(requestedWarehouseId) : Collections.emptySet();
        }

        // 2. Nhóm bị giới hạn theo kho (WAREHOUSE_MANAGER, WAREHOUSE_STAFF, ...)
        Set<Long> userWarehouseIds = user.warehouseIds();
        if (userWarehouseIds == null || userWarehouseIds.isEmpty()) {
            throw ApiException.forbidden("Tài khoản chưa được phân quyền phụ trách bất kỳ kho nào");
        }

        if (requestedWarehouseId != null) {
            if (!userWarehouseIds.contains(requestedWarehouseId)) {
                // Chống tấn công giả mạo ID kho trên URL (IDOR)
                throw ApiException.forbidden("Bạn không có quyền truy cập dữ liệu của kho ID: " + requestedWarehouseId);
            }
            return Set.of(requestedWarehouseId);
        }

        // Mặc định lọc theo tất cả các kho user được phụ trách
        return userWarehouseIds;
    }

    // ==========================================
    // CÁC HÀM XỬ LÝ CHUỖI ĐƠN GIẢN (HELPER)
    // ==========================================

    private static Set<String> parseRoles(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String part : headerValue.split(",")) {
            String role = part.trim();
            if (!role.isEmpty()) {
                result.add(role);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<Long> parseWarehouseIds(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> result = new HashSet<>();
        for (String part : headerValue.split(",")) {
            String idStr = part.trim();
            if (!idStr.isEmpty()) {
                try {
                    result.add(Long.parseLong(idStr));
                } catch (NumberFormatException ignored) {
                    // Bỏ qua giá trị không phải số nguyên
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
