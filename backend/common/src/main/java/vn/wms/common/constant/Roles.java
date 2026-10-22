package vn.wms.common.constant;

import java.util.List;

/**
 * Hằng số vai trò (RBAC) dùng chung cho toàn hệ thống WMS.
 *
 * <p>Giá trị lưu trong DB và trong claim {@code roles} của JWT là tên không tiền tố (ví dụ "ADMIN").
 * Spring Security dùng authority có tiền tố {@link #AUTHORITY_PREFIX} (ví dụ "ROLE_ADMIN"),
 * nên biểu thức hasRole("ADMIN") sẽ khớp với authority "ROLE_ADMIN".
 */
public final class Roles {

    /** Quản trị hệ thống. */
    public static final String ADMIN = "ADMIN";
    /** Quản lý kho. */
    public static final String WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER";
    /** Nhân viên kho. */
    public static final String WAREHOUSE_STAFF = "WAREHOUSE_STAFF";
    /** Nhân viên mua hàng. */
    public static final String PURCHASING = "PURCHASING";
    /** Nhân viên bán hàng. */
    public static final String SALES = "SALES";
    /** Kế toán. */
    public static final String ACCOUNTANT = "ACCOUNTANT";

    /** Tiền tố authority mà Spring Security dùng cho vai trò. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    /** Toàn bộ vai trò hợp lệ của hệ thống. */
    public static final List<String> ALL = List.of(
            ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, PURCHASING, SALES, ACCOUNTANT);

    private Roles() {
        // Lớp tiện ích, không khởi tạo
    }

    /** Kiểm tra tên vai trò có thuộc danh sách vai trò hợp lệ không. */
    public static boolean isValid(String role) {
        return role != null && ALL.contains(role);
    }

    /** Thêm tiền tố ROLE_ để dùng làm authority của Spring Security. */
    public static String toAuthority(String role) {
        return AUTHORITY_PREFIX + role;
    }
}
