package vn.wms.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.entity.User;
import vn.wms.common.constant.Roles;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử toàn diện nghiệp vụ Quản lý Người dùng & Phân quyền theo kho (User & Warehouse RBAC):
 * 1. Nghiệp vụ cá nhân (Self-service): Xem hồ sơ, cập nhật hồ sơ, đổi mật khẩu.
 * 2. Nghiệp vụ quản trị (Admin): Xem danh sách phân trang & bộ lọc, xem chi tiết, tạo người dùng,
 *    cập nhật người dùng, gán/phân quyền kho trực tiếp, khóa/mở khóa tài khoản, và bảo mật RBAC (chặn non-admin).
 * 3. Chặn Admin tự khóa tài khoản, tự gỡ quyền ADMIN và khóa quản trị viên cuối cùng.
 */
@Transactional
class UserApplicationTest extends BaseIntegrationTest {

    // ==========================================
    // 1. NGHIỆP VỤ HỒ SƠ CÁ NHÂN (SELF-SERVICE)
    // ==========================================

    @Test
    void getProfileReturnsCurrentUserDataAndWarehouses() throws Exception {
        String token = loginAndGetToken("admin", "Demopassword");

        mockMvc.perform(get("/api/auth/users/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.email").value("admin@wms.vn"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.warehouseIds", org.hamcrest.Matchers.hasItems(1, 2)));
    }

    @Test
    void updateProfileUpdatesFullNameAndEmail() throws Exception {
        String token = loginAndGetToken("staff", "Demopassword");

        String req = objectMapper.writeValueAsString(Map.of(
                "fullName", "Staff Updated Name",
                "email", "staff.updated@wms.vn"
        ));

        mockMvc.perform(put("/api/auth/users/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Staff Updated Name"))
                .andExpect(jsonPath("$.data.email").value("staff.updated@wms.vn"));

        // Xác nhận đã lưu trong DB
        User staff = userRepository.findByUsername("staff").orElseThrow();
        assertThat(staff.getFullName()).isEqualTo("Staff Updated Name");
        assertThat(staff.getEmail()).isEqualTo("staff.updated@wms.vn");
    }

    @Test
    void changePasswordSuccessAndAllowsLoginWithNewPassword() throws Exception {
        String token = loginAndGetToken("manager", "Demopassword");

        String req = objectMapper.writeValueAsString(Map.of(
                "currentPassword", "Demopassword",
                "newPassword", "NewPass123@",
                "confirmPassword", "NewPass123@"
        ));

        mockMvc.perform(post("/api/auth/users/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Đổi mật khẩu thành công"));

        // Đăng nhập lại bằng mật khẩu mới phải thành công
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("manager", "NewPass123@")))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordFails() throws Exception {
        String token = loginAndGetToken("admin", "Demopassword");

        String req = objectMapper.writeValueAsString(Map.of(
                "currentPassword", "wrong-password",
                "newPassword", "NewPass123@",
                "confirmPassword", "NewPass123@"
        ));

        mockMvc.perform(post("/api/auth/users/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mật khẩu hiện tại không chính xác")));
    }

    // ==========================================
    // 2. NGHIỆP VỤ QUẢN TRỊ NGƯỜI DÙNG & PHÂN QUYỀN KHO (ADMIN)
    // ==========================================

    @Test
    void adminCanListUsersWithPaginationAndFilters() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();

        // 1. Lấy toàn bộ danh sách người dùng (mặc định page=0, size=10)
        mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        // 2. Lọc danh sách theo vai trò WAREHOUSE_STAFF
        mockMvc.perform(get("/api/auth/users?role=" + Roles.WAREHOUSE_STAFF)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].username", org.hamcrest.Matchers.hasItem("staff")));

        // 3. Lọc danh sách theo kho warehouseId=2 (admin và manager có kho 2, staff chỉ có kho 1)
        mockMvc.perform(get("/api/auth/users?warehouseId=2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[*].username", org.hamcrest.Matchers.hasItems("admin", "manager")));
    }

    @Test
    void adminCanGetUserById() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User staff = userRepository.findByUsername("staff").orElseThrow();

        mockMvc.perform(get("/api/auth/users/" + staff.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(staff.getId()))
                .andExpect(jsonPath("$.data.username").value("staff"))
                .andExpect(jsonPath("$.data.email").value("staff@wms.vn"))
                .andExpect(jsonPath("$.data.roles[0]").value(Roles.WAREHOUSE_STAFF))
                .andExpect(jsonPath("$.data.warehouseIds[0]").value(1));
    }

    @Test
    void adminCanCreateNewUserWithWarehouseAssignment() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();

        String req = objectMapper.writeValueAsString(Map.of(
                "username", "test_clerk",
                "email", "test_clerk@wms.vn",
                "password", "ClerkPass123@",
                "fullName", "Test Clerk",
                "roles", Set.of(Roles.WAREHOUSE_STAFF),
                "warehouseIds", Set.of(1, 3)
        ));

        // Admin tạo tài khoản nhân viên mới và gán kho 1 & 3 (trả về HTTP 201 Created)
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("test_clerk"))
                .andExpect(jsonPath("$.data.email").value("test_clerk@wms.vn"))
                .andExpect(jsonPath("$.data.warehouseIds", org.hamcrest.Matchers.hasItems(1, 3)));

        // Đăng nhập bằng tài khoản vừa tạo và kiểm tra JWT Access Token claim warehouseIds
        String loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("test_clerk", "ClerkPass123@")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(loginRes).path("data").path("accessToken").asText();
        Claims claims = jwtService.parse(accessToken, redisTemplate);
        List<Long> warehouseIds = jwtService.warehouseIdsOf(claims);
        assertThat(warehouseIds).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void adminCanUpdateUserAndWarehouses() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User staff = userRepository.findByUsername("staff").orElseThrow();

        String req = objectMapper.writeValueAsString(Map.of(
                "fullName", "Staff Updated By Admin",
                "email", "staff.newemail@wms.vn",
                "roles", Set.of(Roles.WAREHOUSE_STAFF, Roles.WAREHOUSE_MANAGER),
                "warehouseIds", Set.of(1, 2, 5),
                "enabled", true
        ));

        mockMvc.perform(put("/api/auth/users/" + staff.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Staff Updated By Admin"))
                .andExpect(jsonPath("$.data.email").value("staff.newemail@wms.vn"))
                .andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.hasItems(Roles.WAREHOUSE_STAFF, Roles.WAREHOUSE_MANAGER)))
                .andExpect(jsonPath("$.data.warehouseIds", org.hamcrest.Matchers.hasItems(1, 2, 5)));
    }

    @Test
    void adminCanAssignWarehousesDirectly() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User manager = userRepository.findByUsername("manager").orElseThrow();

        String req = objectMapper.writeValueAsString(Map.of(
                "warehouseIds", Set.of(10, 20)
        ));

        // Phân quyền lại danh sách kho trực tiếp qua endpoint /api/auth/users/{id}/warehouses
        mockMvc.perform(put("/api/auth/users/" + manager.getId() + "/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouseIds", org.hamcrest.Matchers.hasItems(10, 20)))
                .andExpect(jsonPath("$.data.warehouseIds.length()").value(2));
    }

    @Test
    void adminCanToggleUserStatusToLockAndUnlock() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User staff = userRepository.findByUsername("staff").orElseThrow();

        // 1. Admin khóa tài khoản (enabled = false)
        mockMvc.perform(patch("/api/auth/users/" + staff.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false, "reasonChange", "Khóa thử nghiệm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 2. Nhân viên đăng nhập khi đã bị khóa -> Nhận 401 Unauthorized
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("staff", "Demopassword")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị khóa hoặc ngừng kích hoạt"));

        // 3. Admin mở khóa lại tài khoản (enabled = true)
        mockMvc.perform(patch("/api/auth/users/" + staff.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true, "reasonChange", "Mở khóa thử nghiệm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        // 4. Nhân viên đăng nhập lại thành công -> 200 OK
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("staff", "Demopassword")))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminCannotAccessUserManagementApis() throws Exception {
        // Staff là tài khoản vai trò WAREHOUSE_STAFF (không có quyền ADMIN)
        String staffToken = loginAndGetToken("staff", "Demopassword");

        // 1. Thử xem danh sách người dùng -> 403 Forbidden
        mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. Thử tạo người dùng -> 403 Forbidden
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "hacker",
                                "email", "hacker@wms.vn",
                                "password", "Hacker123@",
                                "roles", Set.of(Roles.ADMIN)
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 3. Thử phân quyền kho -> 403 Forbidden
        mockMvc.perform(put("/api/auth/users/1/warehouses")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("warehouseIds", Set.of(1, 2)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ==========================================
    // 3. BẢO VỆ ADMIN: CHẶN TỰ KHÓA, TỰ GỠ QUYỀN, KHÓA ADMIN CUỐI CÙNG
    // ==========================================

    @Test
    void adminCannotLockThemselves() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();

        // 1. Thử tự khóa qua PATCH status -> 400 Bad Request
        mockMvc.perform(patch("/api/auth/users/" + admin.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false, "reasonChange", "Tự khóa"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không được phép tự khóa")));

        // 2. Thử tự khóa qua PUT update -> 400 Bad Request
        String updateReq = objectMapper.writeValueAsString(Map.of(
                "email", "admin@wms.vn",
                "roles", Set.of(Roles.ADMIN),
                "enabled", false
        ));
        mockMvc.perform(put("/api/auth/users/" + admin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReq))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không được phép tự khóa")));
    }

    @Test
    void adminCannotRemoveTheirOwnAdminRole() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();

        // Thử tự gỡ quyền ADMIN của chính mình
        String updateReq = objectMapper.writeValueAsString(Map.of(
                "email", "admin@wms.vn",
                "roles", Set.of(Roles.WAREHOUSE_STAFF)
        ));
        mockMvc.perform(put("/api/auth/users/" + admin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReq))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không được phép tự gỡ vai trò ADMIN")));
    }

    @Test
    void cannotLockOrDemoteTheLastActiveAdmin() throws Exception {
        // Tạo thêm admin phụ để admin chính thao tác
        User admin2 = new User();
        admin2.setUsername("admin_second");
        admin2.setEmail("admin2@wms.vn");
        admin2.setPasswordHash(passwordEncoder.encode("Demopassword"));
        admin2.setEnabled(true);
        admin2.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.ADMIN).orElseThrow())));
        admin2.setCompany(companyA);
        userRepository.save(admin2);

        String adminToken = loginAndGetToken("admin", "Demopassword");
        User admin = userRepository.findByUsername("admin").orElseThrow();

        // Khi có 2 admin: admin có thể khóa admin2
        mockMvc.perform(patch("/api/auth/users/" + admin2.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false, "reasonChange", "Khóa admin phụ"))))
                .andExpect(status().isOk());

        // Hiện tại chỉ còn 1 admin active ("admin"). Thử gỡ quyền ADMIN của admin -> bị chặn
        String req = objectMapper.writeValueAsString(Map.of(
                "email", "admin@wms.vn",
                "roles", Set.of(Roles.WAREHOUSE_STAFF)
        ));
        mockMvc.perform(put("/api/auth/users/" + admin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("quản trị viên")));
    }
}
