package vn.wms.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.wms.auth.dto.auth.RegisterRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử toàn diện nghiệp vụ Xác thực (Authentication & JWT):
 * Đăng nhập DB thật, phân quyền RBAC, kiểm tra rate-limit, làm mới token,
 * xoay vòng Refresh Token (RTR), thu hồi token khi logout, và đăng ký tài khoản (Admin only).
 */
class AuthServiceApplicationTest extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
    }

    @Test
    void livenessProbeIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginWithDatabaseAccountReturnsValidJwt() throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "Demopassword")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(res).path("data").path("accessToken").asText();
        Claims claims = jwtService.parse(token);
        assertThat(jwtService.warehouseIdsOf(claims)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void loginWithEmailReturnsValidJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin@wms.vn", "Demopassword")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));
    }

    @Test
    void loginWithWrongPasswordIsRejectedAndCountsRateLimit() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "sai-mat-khau")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("còn 4 lần thử")));
    }

    @Test
    void loginExceedingMaxAttemptsLocksAccount() throws Exception {
        String badPassword = "wrong_password";
        // Thử sai 4 lần đầu
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("admin", badPassword)))
                    .andExpect(status().isUnauthorized());
        }

        // Lần thứ 5 sai -> Phải bị khóa tạm thời (400 Bad Request)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", badPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("30 phút")));
    }

    @Test
    void loginWithUnknownUserIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nguoi-la", "Demopassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithBlankFieldsFailsValidation() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meWithValidTokenReturnsUsernameAndRoles() throws Exception {
        String token = loginAndGetToken("admin", "Demopassword");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));
    }

    @Test
    void logoutViaPostSuccessAndBlacklistsToken() throws Exception {
        String token = loginAndGetToken("admin", "Demopassword");

        // Gọi POST /api/auth/logout
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Đăng xuất tài khoản thành công"));

        // Sau khi logout, token đã nằm trong blacklist -> Gọi /api/auth/me phải bị 401
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenSuccessAndRotatesToken() throws Exception {
        // Đăng nhập lấy refresh token
        String loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "Demopassword")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(loginRes).path("data");
        String oldRefreshToken = data.path("refreshToken").asText();
        assertThat(oldRefreshToken).isNotBlank();

        // Gửi POST /api/auth/refresh với Refresh Token cũ
        String refreshReq = objectMapper.writeValueAsString(Map.of("refreshToken", oldRefreshToken));
        String refreshRes = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode refreshData = objectMapper.readTree(refreshRes).path("data");
        String newAccessToken = refreshData.path("accessToken").asText();
        String newRefreshToken = refreshData.path("refreshToken").asText();

        // Kiểm tra token mới dùng được để gọi /api/auth/me
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));

        // Do có xoay vòng token (RTR), Refresh Token cũ không còn dùng được nữa
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshReq))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenWithInvalidTokenFails() throws Exception {
        String invalidReq = objectMapper.writeValueAsString(Map.of("refreshToken", "token.khong.hop.le"));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidReq))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenAfterLogoutFailsBecauseRevoked() throws Exception {
        // 1. Đăng nhập
        String loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "Demopassword")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(loginRes).path("data");
        String accessToken = data.path("accessToken").asText();
        String refreshToken = data.path("refreshToken").asText();

        // 2. Đăng xuất (xóa refresh token khỏi Redis)
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 3. Cố gắng refresh bằng refresh token đó -> phải bị 401 Unauthorized
        String refreshReq = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshReq))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerByAdminCreatesUserSuccessfully() throws Exception {
        String adminToken = loginAndGetToken("admin", "Demopassword");

        RegisterRequest request = new RegisterRequest(
                "new_manager",
                "new_manager@wms.vn",
                "Password123@",
                "New Warehouse Manager",
                Set.of("WAREHOUSE_MANAGER")
        );

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("new_manager"))
                .andExpect(jsonPath("$.data.roles[0]").value("WAREHOUSE_MANAGER"));

        // Kiểm tra user đã được lưu vào database
        assertThat(userRepository.findByUsername("new_manager")).isPresent();
    }

    @Test
    void registerByStaffReturnsForbidden() throws Exception {
        String staffToken = loginAndGetToken("staff", "Demopassword");

        RegisterRequest request = new RegisterRequest(
                "unauthorized_user",
                "unauthorized@wms.vn",
                "Password123@",
                "Unauthorized User",
                Set.of("WAREHOUSE_STAFF")
        );

        // Staff không có quyền ADMIN -> phải bị 403 Forbidden
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
