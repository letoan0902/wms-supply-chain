package vn.wms.auth.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.entity.User;
import vn.wms.common.constant.Roles;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint nội bộ tra người nhận email cảnh báo tồn kho (WMS-15.2): đúng vai trò, đúng kho, đúng doanh nghiệp, bỏ
 * tài khoản bị khóa và email không gửi được, không lộ mật khẩu băm.
 */
class AlertRecipientIntegrationTest extends BaseIntegrationTest {

    private static final long WAREHOUSE = 7001L;
    private static final long OTHER_WAREHOUSE = 7002L;
    private static final String URL = "/internal/auth/users/alert-recipients";

    @BeforeEach
    void seedUsers() {
        user("ar_manager", "ql.kho7001@congty-a.vn", Roles.WAREHOUSE_MANAGER, Set.of(WAREHOUSE), true, companyA);
        user("ar_manager_other", "ql.kho7002@congty-a.vn", Roles.WAREHOUSE_MANAGER, Set.of(OTHER_WAREHOUSE), true, companyA);
        user("ar_manager_locked", "ql.khoa@congty-a.vn", Roles.WAREHOUSE_MANAGER, Set.of(WAREHOUSE), false, companyA);
        user("ar_staff", "nv.kho7001@congty-a.vn", Roles.WAREHOUSE_STAFF, Set.of(WAREHOUSE), true, companyA);
        user("ar_admin_local", "admin2@wms.local", Roles.ADMIN, Set.of(), true, companyA);
        user("ar_admin_locked", "admin3@congty-a.vn", Roles.ADMIN, Set.of(), false, companyA);
        user("ar_manager_b", "ql.kho7001@congty-b.vn", Roles.WAREHOUSE_MANAGER, Set.of(WAREHOUSE), true, companyB);
        user("ar_admin_b", "admin@congty-b.vn", Roles.ADMIN, Set.of(), true, companyB);
    }

    private void user(String username, String email, String role, Set<Long> warehouses, boolean enabled, Company company) {
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setFullName("Người dùng " + username);
        user.setEnabled(enabled);
        user.setRoles(new HashSet<>(Set.of(roleRepository.findByName(role).orElseThrow())));
        user.setWarehouseIds(new HashSet<>(warehouses));
        if (user.getId() == null) {
            user.setCompany(company);
        }
        userRepository.save(user);
    }

    @Test
    void returnsManagersOfTheWarehouseAndAdminsOfTheCompanyOnly() throws Exception {
        // Công ty A: admin mẫu (admin@wms.vn) và quản lý kho 7001; không có nhân viên, quản lý kho khác, tài khoản bị
        // khóa, email .local hay người của công ty B
        // (cơ sở dữ liệu test dùng chung với lớp khác nên có thể có thêm admin của công ty A)
        mockMvc.perform(get(URL).param("warehouseId", String.valueOf(WAREHOUSE)).header("X-Company-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email", hasItems("admin@wms.vn", "ql.kho7001@congty-a.vn")))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("ql.kho7002@congty-a.vn"))))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("ql.khoa@congty-a.vn"))))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("nv.kho7001@congty-a.vn"))))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("admin2@wms.local"))))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("admin3@congty-a.vn"))))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("ql.kho7001@congty-b.vn"))))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("admin@congty-b.vn"))))
                .andExpect(jsonPath("$.data[?(@.email == 'ql.kho7001@congty-a.vn')].role", contains("WAREHOUSE_MANAGER")))
                .andExpect(jsonPath("$.data[?(@.email == 'ql.kho7001@congty-a.vn')].fullName",
                        contains("Người dùng ar_manager")))
                .andExpect(jsonPath("$.data[?(@.email == 'admin@wms.vn')].role", contains("ADMIN")))
                .andExpect(jsonPath("$.data[0].role", is("ADMIN")));

        // Công ty B cùng ID kho: chỉ người của B
        mockMvc.perform(get(URL).param("warehouseId", String.valueOf(WAREHOUSE)).header("X-Company-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email", hasItems("admin@congty-b.vn", "ql.kho7001@congty-b.vn")))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("ql.kho7001@congty-a.vn"))))
                .andExpect(jsonPath("$.data[*].email", not(hasItem("admin@wms.vn"))));

        // Không truyền kho: chỉ admin
        mockMvc.perform(get(URL).header("X-Company-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email", hasItem("admin@wms.vn")))
                .andExpect(jsonPath("$.data[*].role", everyItem(is("ADMIN"))));
    }

    @Test
    void neverExposesPasswordHashOrUsername() throws Exception {
        String body = mockMvc.perform(get(URL).param("warehouseId", String.valueOf(WAREHOUSE))
                        .header("X-Company-Id", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("$2a$").doesNotContain("\"username\"")
                .doesNotContain("\"id\"").doesNotContain("warehouseIds");
    }

    @Test
    void companyIsRequired() throws Exception {
        mockMvc.perform(get(URL).param("warehouseId", String.valueOf(WAREHOUSE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("COMPANY_REQUIRED")));
    }

    @Test
    void undeliverableAddressesAreRejected() {
        assertThat(AlertRecipientService.isDeliverable("ql@congty.vn")).isTrue();
        assertThat(AlertRecipientService.isDeliverable(" QL@CongTy.com.vn ")).isTrue();
        assertThat(AlertRecipientService.isDeliverable("admin_b@wms.local")).isFalse();
        assertThat(AlertRecipientService.isDeliverable("demo@example.com")).isFalse();
        assertThat(AlertRecipientService.isDeliverable("khong-hop-le")).isFalse();
        assertThat(AlertRecipientService.isDeliverable("")).isFalse();
        assertThat(AlertRecipientService.isDeliverable(null)).isFalse();
    }
}
