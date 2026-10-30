package vn.wms.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.auth.dto.RegisterRequest;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.security.JwtService;
import vn.wms.common.constant.Roles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra toàn diện luồng xác thực: Đăng nhập DB thật, phân quyền RBAC,
 * đăng ký tài khoản (Admin only), đăng xuất POST (blacklist Redis), và Rate-limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> mockRedisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        mockRedisStore.clear();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Giả lập Redis: hasKey, delete, get, set, increment trong bộ nhớ test
        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisStore.containsKey(key);
        });

        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisStore.remove(key) != null;
        });

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            mockRedisStore.put(key, value);
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any());

        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String current = mockRedisStore.get(key);
            long count = (current != null) ? Long.parseLong(current) + 1 : 1L;
            mockRedisStore.put(key, String.valueOf(count));
            return count;
        });

        // Khởi tạo các vai trò chuẩn của hệ thống
        for (String roleName : Roles.ALL) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                Role role = new Role();
                role.setName(roleName);
                role.setDescription("Role " + roleName);
                roleRepository.save(role);
            }
        }

        // Seed tài khoản admin
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@wms.vn");
            admin.setPasswordHash(passwordEncoder.encode("Demopassword"));
            admin.setFullName("Admin User");
            admin.setEnabled(true);
            admin.setRoles(Set.of(roleRepository.findByName(Roles.ADMIN).orElseThrow()));
            userRepository.save(admin);
        }

        // Seed tài khoản staff
        if (userRepository.findByUsername("staff").isEmpty()) {
            User staff = new User();
            staff.setUsername("staff");
            staff.setEmail("staff@wms.vn");
            staff.setPasswordHash(passwordEncoder.encode("Demopassword"));
            staff.setFullName("Staff User");
            staff.setEnabled(true);
            staff.setRoles(Set.of(roleRepository.findByName(Roles.WAREHOUSE_STAFF).orElseThrow()));
            userRepository.save(staff);
        }
    }

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("accessToken").asText();
    }

    @Test
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
    }

    @Test
    void loginWithDatabaseAccountReturnsValidJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "Demopassword")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
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

    @Test
    void livenessProbeIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
