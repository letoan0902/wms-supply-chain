package vn.wms.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bindRequest(String username, String roles, String warehouseIds) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (username != null) {
            request.addHeader(UserContext.HEADER_USER_NAME, username);
        }
        if (roles != null) {
            request.addHeader(UserContext.HEADER_USER_ROLES, roles);
        }
        if (warehouseIds != null) {
            request.addHeader(UserContext.HEADER_USER_WAREHOUSE_IDS, warehouseIds);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void getCurrentUserReturnsEmptyWhenNoRequestAttributes() {
        Optional<CurrentUser> userOpt = UserContext.getCurrentUser();
        assertThat(userOpt).isEmpty();
    }

    @Test
    void getCurrentUserReturnsEmptyWhenUsernameHeaderIsMissingOrBlank() {
        bindRequest(null, "ADMIN", "1,2");
        assertThat(UserContext.getCurrentUser()).isEmpty();

        bindRequest("   ", "ADMIN", "1,2");
        assertThat(UserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void getCurrentUserParsesUsernameRolesAndWarehouseIdsCorrectly() {
        bindRequest("staff01", "WAREHOUSE_STAFF, SALES", "1, 2, abc, 3");

        CurrentUser user = UserContext.requireCurrentUser();
        assertThat(user.username()).isEqualTo("staff01");
        assertThat(user.roles()).containsExactlyInAnyOrder("WAREHOUSE_STAFF", "SALES");
        assertThat(user.warehouseIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(user.hasRole("WAREHOUSE_STAFF")).isTrue();
        assertThat(user.hasRole("ADMIN")).isFalse();
    }

    @Test
    void isGlobalScopeReturnsTrueOnlyForAdminAndAccountant() {
        CurrentUser admin = new CurrentUser("admin", Set.of(Roles.ADMIN), Set.of());
        CurrentUser accountant = new CurrentUser("acc", Set.of(Roles.ACCOUNTANT), Set.of());
        CurrentUser manager = new CurrentUser("mgr", Set.of(Roles.WAREHOUSE_MANAGER), Set.of(1L));
        CurrentUser staff = new CurrentUser("staff", Set.of(Roles.WAREHOUSE_STAFF), Set.of(1L));

        assertThat(UserContext.isGlobalScope(admin)).isTrue();
        assertThat(UserContext.isGlobalScope(accountant)).isTrue();
        assertThat(UserContext.isGlobalScope(manager)).isFalse();
        assertThat(UserContext.isGlobalScope(staff)).isFalse();
        assertThat(UserContext.isGlobalScope(null)).isFalse();
    }

    @Test
    void resolveAllowedWarehousesForAdminOrAccountant() {
        CurrentUser admin = new CurrentUser("admin", Set.of(Roles.ADMIN), Set.of());

        // Khi Admin không truyền kho -> Trả về rỗng (nghĩa là không lọc, lấy tất cả các kho)
        Set<Long> allWarehouses = UserContext.resolveAllowedWarehouses(admin, null);
        assertThat(allWarehouses).isEmpty();

        // Khi Admin yêu cầu xem riêng kho ID = 3
        Set<Long> specificWarehouse = UserContext.resolveAllowedWarehouses(admin, 3L);
        assertThat(specificWarehouse).containsExactly(3L);
    }

    @Test
    void resolveAllowedWarehousesForStaffOrManager() {
        CurrentUser staff = new CurrentUser("staff", Set.of(Roles.WAREHOUSE_STAFF), Set.of(1L, 2L));

        // 1. Nhân viên không truyền kho -> Tự động trả về toàn bộ danh sách kho được gán {1, 2}
        Set<Long> defaultWarehouses = UserContext.resolveAllowedWarehouses(staff, null);
        assertThat(defaultWarehouses).containsExactlyInAnyOrder(1L, 2L);

        // 2. Nhân viên truyền kho ID = 2 (nằm trong {1, 2}) -> Trả về đúng kho {2}
        Set<Long> allowedWarehouse = UserContext.resolveAllowedWarehouses(staff, 2L);
        assertThat(allowedWarehouse).containsExactly(2L);

        // 3. Nhân viên cố tình truyền kho ID = 5 (không thuộc {1, 2}) -> Chặn đứng bằng lỗi 403 Forbidden (Chống IDOR)
        assertThatThrownBy(() -> UserContext.resolveAllowedWarehouses(staff, 5L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Bạn không có quyền truy cập dữ liệu của kho ID: 5");
    }

    @Test
    void resolveAllowedWarehousesWhenStaffHasNoAssignedWarehousesFailsWithForbidden() {
        CurrentUser unassignedStaff = new CurrentUser("staff_unassigned", Set.of(Roles.WAREHOUSE_STAFF), Set.of());

        assertThatThrownBy(() -> UserContext.resolveAllowedWarehouses(unassignedStaff, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tài khoản chưa được phân quyền phụ trách bất kỳ kho nào");
    }

    @Test
    void resolveAllowedWarehousesWhenUserIsNullFailsWithUnauthorized() {
        assertThatThrownBy(() -> UserContext.resolveAllowedWarehouses(null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chưa đăng nhập");
    }

    @Test
    void requireWarehouseWriteAccessAllowsAdminAndAssignedManagerOnly() {
        CurrentUser admin = new CurrentUser("admin", Set.of(Roles.ADMIN), Set.of());
        CurrentUser manager = new CurrentUser("mgr", Set.of(Roles.WAREHOUSE_MANAGER), Set.of(1L, 2L));
        CurrentUser accountant = new CurrentUser("acc", Set.of(Roles.ACCOUNTANT), Set.of());
        CurrentUser staff = new CurrentUser("staff", Set.of(Roles.WAREHOUSE_STAFF), Set.of(1L));

        // 1. Admin được phép ghi mọi kho
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> UserContext.requireWarehouseWriteAccess(admin, 1L));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> UserContext.requireWarehouseWriteAccess(admin, 999L));

        // 2. Manager được phép ghi kho được gán {1, 2}
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> UserContext.requireWarehouseWriteAccess(manager, 1L));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> UserContext.requireWarehouseWriteAccess(manager, 2L));

        // 3. Manager không được phép ghi kho ngoài phạm vi gán {3}
        assertThatThrownBy(() -> UserContext.requireWarehouseWriteAccess(manager, 3L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tài khoản không có quyền thay đổi dữ liệu của kho ID: 3");

        // 4. Kế toán (ACCOUNTANT) có quyền đọc nhưng bị cấm ghi (403 Forbidden)
        assertThatThrownBy(() -> UserContext.requireWarehouseWriteAccess(accountant, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tài khoản không có quyền thay đổi dữ liệu của kho ID: 1");

        // 5. Nhân viên (STAFF) bị cấm ghi cấu hình kho / vị trí (403 Forbidden)
        assertThatThrownBy(() -> UserContext.requireWarehouseWriteAccess(staff, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tài khoản không có quyền thay đổi dữ liệu của kho ID: 1");
    }
}
