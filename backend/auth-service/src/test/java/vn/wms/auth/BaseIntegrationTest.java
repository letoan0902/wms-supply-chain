package vn.wms.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.security.JwtService;
import vn.wms.common.constant.Roles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lớp cấu hình tích hợp dùng chung cho các bộ kiểm thử của auth-service:
 * Thiết lập SpringBootTest, MockMvc, Mock Redis và dữ liệu khởi tạo (Roles, hai doanh nghiệp mẫu,
 * Admin, Staff, Manager thuộc Công ty mẫu A và admin_b thuộc Công ty mẫu B).
 *
 * <p>Test chạy H2 và không chạy Flyway nên phải tự tạo hai doanh nghiệp (id 1 và 2, giống migration V4)
 * trước khi tạo tài khoản.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    /** Mật khẩu của các tài khoản seed trong test (chỉ dùng cho H2 trong bộ nhớ). */
    protected static final String DEFAULT_PASSWORD = "Demopassword";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected CompanyRepository companyRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    /** Công ty mẫu A (id = 1): admin, staff, manager. */
    protected Company companyA;

    /** Công ty mẫu B (id = 2): admin_b. */
    protected Company companyB;

    @MockBean(name = "redisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @MockBean
    protected ValueOperations<String, String> valueOperations;

    protected final Map<String, String> mockRedisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        mockRedisStore.clear();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisStore.containsKey(key);
        });

        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisStore.remove(key) != null;
        });

        when(valueOperations.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisStore.get(key);
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

        // Hai doanh nghiệp mẫu với ID cố định như migration V4; MERGE đưa trạng thái về ACTIVE mỗi lần chạy
        companyA = upsertCompany(1L, "CTY000001", "Công ty mẫu A");
        companyB = upsertCompany(2L, "CTY000002", "Công ty mẫu B");

        // Seed tài khoản admin
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@wms.vn");
            admin.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            admin.setFullName("Admin User");
            admin.setEnabled(true);
            admin.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.ADMIN).orElseThrow())));
            admin.setWarehouseIds(new java.util.HashSet<>(Set.of(1L, 2L)));
            admin.setCompany(companyA);
            userRepository.save(admin);
        }

        // Seed tài khoản staff
        if (userRepository.findByUsername("staff").isEmpty()) {
            User staff = new User();
            staff.setUsername("staff");
            staff.setEmail("staff@wms.vn");
            staff.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            staff.setFullName("Staff User");
            staff.setEnabled(true);
            staff.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.WAREHOUSE_STAFF).orElseThrow())));
            staff.setWarehouseIds(new java.util.HashSet<>(Set.of(1L)));
            staff.setCompany(companyA);
            userRepository.save(staff);
        }

        // Seed tài khoản manager
        if (userRepository.findByUsername("manager").isEmpty()) {
            User manager = new User();
            manager.setUsername("manager");
            manager.setEmail("manager@wms.vn");
            manager.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            manager.setFullName("Manager User");
            manager.setEnabled(true);
            manager.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.WAREHOUSE_MANAGER).orElseThrow())));
            manager.setWarehouseIds(new java.util.HashSet<>(Set.of(1L, 2L)));
            manager.setCompany(companyA);
            userRepository.save(manager);
        }

        // Seed tài khoản admin_b của Công ty mẫu B
        if (userRepository.findByUsername("admin_b").isEmpty()) {
            User adminB = new User();
            adminB.setUsername("admin_b");
            adminB.setEmail("admin_b@wms.local");
            adminB.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            adminB.setFullName("Admin Company B");
            adminB.setEnabled(true);
            adminB.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.ADMIN).orElseThrow())));
            adminB.setWarehouseIds(new java.util.HashSet<>(Set.of(3L)));
            adminB.setCompany(companyB);
            userRepository.save(adminB);
        }
    }

    private Company upsertCompany(Long id, String code, String name) {
        jdbcTemplate.update("MERGE INTO companies (id, code, name, status, created_at, updated_at, created_by, updated_by) "
                + "KEY (id) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system')",
                id, code, name);
        return companyRepository.findById(id).orElseThrow();
    }

    /** Đổi trạng thái doanh nghiệp trực tiếp trong cơ sở dữ liệu (giống thao tác tay của tiêu chí nghiệm thu 4). */
    protected void setCompanyStatus(Long companyId, String status) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE companies SET status = ? WHERE id = ?", status, companyId);
        // Bỏ bản sao Company trong bộ nhớ đệm của Hibernate để lần đọc sau lấy trạng thái mới từ cơ sở dữ liệu
        entityManager.clear();
    }

    protected String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    protected String loginAndGetToken(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("accessToken").asText();
    }
}
