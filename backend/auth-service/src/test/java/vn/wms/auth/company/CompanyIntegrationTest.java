package vn.wms.auth.company;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.entity.User;
import vn.wms.common.constant.Roles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử WMS-22.2: tài khoản thuộc doanh nghiệp, JWT mang companyId, API người dùng tách theo doanh nghiệp.
 * Dữ liệu: Công ty mẫu A (id 1) có admin, staff, manager; Công ty mẫu B (id 2) có admin_b (xem BaseIntegrationTest).
 */
@Transactional
class CompanyIntegrationTest extends BaseIntegrationTest {

    // ==========================================
    // Tiêu chí 1: token và kết quả đăng nhập mang doanh nghiệp
    // ==========================================

    @Test
    void loginReturnsTokenWithCompanyClaimsAndCompanyObject() throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.id").value(1))
                .andExpect(jsonPath("$.data.company.code").value("CTY000001"))
                .andExpect(jsonPath("$.data.company.name").value("Công ty mẫu A"))
                .andExpect(jsonPath("$.data.companyId").doesNotExist())
                .andExpect(jsonPath("$.data.companyCode").doesNotExist())
                .andExpect(jsonPath("$.data.companyName").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Claims claims = jwtService.parse(objectMapper.readTree(res).path("data").path("accessToken").asText());
        assertThat(claims.get("companyId")).isInstanceOf(Number.class);
        assertThat(jwtService.companyIdOf(claims)).isEqualTo(1L);
        assertThat(claims.get("companyCode", String.class)).isEqualTo("CTY000001");
        assertThat(claims.get("type", String.class)).isEqualTo("ACCESS");
    }

    @Test
    void loginOfCompanyBAdminCarriesCompanyB() throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin_b", DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.id").value(2))
                .andExpect(jsonPath("$.data.company.code").value("CTY000002"))
                .andReturn().getResponse().getContentAsString();

        Claims claims = jwtService.parse(objectMapper.readTree(res).path("data").path("accessToken").asText());
        assertThat(jwtService.companyIdOf(claims)).isEqualTo(2L);
        assertThat(jwtService.companyCodeOf(claims)).isEqualTo("CTY000002");
    }

    @Test
    void meIncludesCompany() throws Exception {
        String token = loginAndGetToken("staff", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("staff"))
                .andExpect(jsonPath("$.data.company.id").value(1))
                .andExpect(jsonPath("$.data.company.code").value("CTY000001"))
                .andExpect(jsonPath("$.data.company.name").value("Công ty mẫu A"));
    }

    // ==========================================
    // Tiêu chí 2: ADMIN chỉ thấy và thao tác trên tài khoản cùng doanh nghiệp
    // ==========================================

    @Test
    void adminOfCompanyAListsOnlyCompanyAUsers() throws Exception {
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/users?size=100").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].username", hasItems("admin", "staff", "manager")))
                .andExpect(jsonPath("$.data.content[*].username", not(hasItem("admin_b"))))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // Lọc theo từ khóa và vai trò cũng không lộ tài khoản của doanh nghiệp khác
        mockMvc.perform(get("/api/users?keyword=admin_b").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/users?role=" + Roles.ADMIN).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].username", not(hasItem("admin_b"))));
        mockMvc.perform(get("/api/users?warehouseId=3").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // Ngược lại, admin_b chỉ thấy tài khoản của Công ty mẫu B
        String adminBToken = loginAndGetToken("admin_b", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/auth/users?size=100").header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].username").value("admin_b"));
    }

    @Test
    void adminOfCompanyAGetsNotFoundForCompanyBUser() throws Exception {
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);
        Long adminBId = userRepository.findByUsername("admin_b").orElseThrow().getId();

        // Xem
        mockMvc.perform(get("/api/users/" + adminBId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Sửa
        mockMvc.perform(put("/api/users/" + adminBId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "hijacked@wms.vn",
                                "fullName", "Hijacked",
                                "roles", Set.of(Roles.WAREHOUSE_STAFF)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Khóa
        mockMvc.perform(patch("/api/users/" + adminBId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Gán kho
        mockMvc.perform(put("/api/users/" + adminBId + "/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("warehouseIds", Set.of(1, 2)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Tài khoản của B không bị thay đổi gì
        entityManager.flush();
        entityManager.clear();
        User adminB = userRepository.findById(adminBId).orElseThrow();
        assertThat(adminB.isEnabled()).isTrue();
        assertThat(adminB.getEmail()).isEqualTo("admin_b@wms.local");
        assertThat(adminB.getWarehouseIds()).containsExactly(3L);
        assertThat(adminB.getRoles()).extracting("name").containsExactly(Roles.ADMIN);
    }

    @Test
    void adminOfCompanyBGetsNotFoundForCompanyAUser() throws Exception {
        String adminBToken = loginAndGetToken("admin_b", DEFAULT_PASSWORD);
        Long staffId = userRepository.findByUsername("staff").orElseThrow().getId();

        mockMvc.perform(get("/api/auth/users/" + staffId).header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/auth/users/" + staffId + "/status")
                        .header("Authorization", "Bearer " + adminBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // Tiêu chí 3: tài khoản mới thuộc doanh nghiệp của ADMIN đang tạo
    // ==========================================

    @Test
    void createdUserBelongsToCreatorCompanyEvenWhenBodySendsAnotherCompanyId() throws Exception {
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("username", "clerk_a");
        body.put("email", "clerk_a@wms.vn");
        body.put("password", "ClerkPass123@");
        body.put("fullName", "Clerk A");
        body.put("roles", Set.of(Roles.WAREHOUSE_STAFF));
        body.put("companyId", 2);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("clerk_a"))
                .andExpect(jsonPath("$.data.companyId").value(1));

        assertThat(userRepository.findByUsername("clerk_a").orElseThrow().getCompanyId()).isEqualTo(1L);

        // Tài khoản vừa tạo đăng nhập thì token mang doanh nghiệp A
        String res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("clerk_a", "ClerkPass123@")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.id").value(1))
                .andReturn().getResponse().getContentAsString();
        Claims claims = jwtService.parse(objectMapper.readTree(res).path("data").path("accessToken").asText());
        assertThat(jwtService.companyIdOf(claims)).isEqualTo(1L);
    }

    @Test
    void registerAssignsCreatorCompanyAndIgnoresCompanyIdInBody() throws Exception {
        String adminBToken = loginAndGetToken("admin_b", DEFAULT_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("username", "clerk_b");
        body.put("email", "clerk_b@wms.vn");
        body.put("password", "ClerkPass123@");
        body.put("fullName", "Clerk B");
        body.put("roles", Set.of(Roles.SALES));
        body.put("companyId", 1);

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("clerk_b"));

        assertThat(userRepository.findByUsername("clerk_b").orElseThrow().getCompanyId()).isEqualTo(2L);

        // admin của A không thấy tài khoản vừa tạo ở B
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/users?keyword=clerk_b").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ==========================================
    // Tiêu chí 4: doanh nghiệp không ACTIVE thì không đăng nhập được
    // ==========================================

    @Test
    void loginIsRejectedWhenCompanySuspended() throws Exception {
        setCompanyStatus(2L, "SUSPENDED");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin_b", DEFAULT_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));

        // Doanh nghiệp A không bị ảnh hưởng
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", DEFAULT_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsRejectedWhenCompanyPending() throws Exception {
        setCompanyStatus(2L, "PENDING");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin_b@wms.local", DEFAULT_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
    }

    @Test
    void wrongPasswordOnSuspendedCompanyStillReturnsUnauthorized() throws Exception {
        setCompanyStatus(2L, "SUSPENDED");

        // Sai mật khẩu vẫn trả lỗi như cũ, không để lộ trạng thái doanh nghiệp
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin_b", "sai-mat-khau")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void refreshIsRejectedWhenCompanySuspendedAfterLogin() throws Exception {
        JsonNode data = loginData("admin_b");
        setCompanyStatus(2L, "SUSPENDED");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", data.path("refreshToken").asText()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
    }

    // ==========================================
    // Tiêu chí 5: làm mới token giữ companyId
    // ==========================================

    @Test
    void refreshKeepsCompanyClaims() throws Exception {
        JsonNode data = loginData("admin_b");

        String res = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", data.path("refreshToken").asText()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Claims claims = jwtService.parse(objectMapper.readTree(res).path("data").path("accessToken").asText());
        assertThat(jwtService.companyIdOf(claims)).isEqualTo(2L);
        assertThat(jwtService.companyCodeOf(claims)).isEqualTo("CTY000002");
        assertThat(claims.getSubject()).isEqualTo("admin_b");
    }

    // ==========================================
    // Tiêu chí 6 (mục API): GET /api/auth/company trả doanh nghiệp của mình
    // ==========================================

    @Test
    void getOwnCompanyReturnsCallerCompany() throws Exception {
        String staffToken = loginAndGetToken("staff", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/auth/company").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("CTY000001"))
                .andExpect(jsonPath("$.data.name").value("Công ty mẫu A"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        String adminBToken = loginAndGetToken("admin_b", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/auth/company").header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.code").value("CTY000002"))
                .andExpect(jsonPath("$.data.name").value("Công ty mẫu B"));
    }

    @Test
    void getOwnCompanyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/company"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminUpdatesOnlyOwnCompany() throws Exception {
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("id", 2);
        body.put("name", "Công ty mẫu A đổi tên");
        body.put("taxCode", "0100000001");
        body.put("phone", "0240000000");
        body.put("address", "Hà Nội");
        body.put("status", "SUSPENDED");

        mockMvc.perform(put("/api/auth/company")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Công ty mẫu A đổi tên"))
                .andExpect(jsonPath("$.data.taxCode").value("0100000001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        entityManager.flush();
        entityManager.clear();
        assertThat(companyRepository.findById(2L).orElseThrow().getName()).isEqualTo("Công ty mẫu B");
    }

    @Test
    void nonAdminCannotUpdateCompany() throws Exception {
        String staffToken = loginAndGetToken("staff", DEFAULT_PASSWORD);

        mockMvc.perform(put("/api/auth/company")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Đổi tên trái phép"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ==========================================
    // Quy tắc "ADMIN cuối cùng" tính trong từng doanh nghiệp
    // ==========================================

    @Test
    void lastActiveAdminRuleIsCountedPerCompany() throws Exception {
        // Công ty A có thêm admin_a2; Công ty B có admin_b (đang hoạt động, không được tính cho A)
        User adminA2 = new User();
        adminA2.setUsername("admin_a2");
        adminA2.setEmail("admin_a2@wms.vn");
        adminA2.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        adminA2.setEnabled(true);
        adminA2.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.ADMIN).orElseThrow())));
        adminA2.setCompany(companyA);
        userRepository.save(adminA2);

        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);
        String adminA2Token = loginAndGetToken("admin_a2", DEFAULT_PASSWORD);
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        // admin gỡ vai trò ADMIN của admin_a2: A còn 2 ADMIN đang hoạt động nên được phép
        mockMvc.perform(put("/api/users/" + adminA2.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "admin_a2@wms.vn",
                                "roles", Set.of(Roles.WAREHOUSE_STAFF)))))
                .andExpect(status().isOk());

        // admin_a2 dùng token cũ (vẫn mang vai trò ADMIN) để khóa admin: A chỉ còn admin là ADMIN đang hoạt động.
        // Nếu đếm trên toàn hệ thống thì admin_b của B làm số ADMIN thành 2 và thao tác lọt qua.
        mockMvc.perform(patch("/api/users/" + adminId + "/status")
                        .header("Authorization", "Bearer " + adminA2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("duy nhất")));

        assertThat(userRepository.findByUsername("admin").orElseThrow().isEnabled()).isTrue();
    }

    private JsonNode loginData(String username) throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data");
    }
}
