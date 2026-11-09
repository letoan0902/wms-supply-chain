package vn.wms.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.security.JwtService;
import vn.wms.auth.subscription.entity.Plan;
import vn.wms.auth.subscription.repository.PlanRepository;
import vn.wms.common.constant.Roles;

import java.math.BigDecimal;
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
 * Thiết lập SpringBootTest, MockMvc, Mock Redis và dữ liệu khởi tạo (Roles, Admin, Staff, Manager).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

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
    protected PlanRepository planRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @MockBean(name = "redisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @MockBean
    protected ValueOperations<String, String> valueOperations;

    @MockBean
    protected HashOperations<String, Object, Object> hashOperations;

    protected final Map<String, String> mockRedisStore = new HashMap<>();
    protected final Map<String, Map<String, String>> mockRedisHashMap = new HashMap<>();

    @BeforeEach
    void setUp() {
        mockRedisStore.clear();
        mockRedisHashMap.clear();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Map<String, String> m = invocation.getArgument(1);
            mockRedisHashMap.computeIfAbsent(key, k -> new HashMap<>()).putAll(m);
            return null;
        }).when(hashOperations).putAll(anyString(), any());

        when(hashOperations.entries(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisHashMap.getOrDefault(key, Map.of());
        });

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

        // Khởi tạo các gói dịch vụ chuẩn nếu chưa có
        if (planRepository.findById("FREE").isEmpty()) {
            planRepository.save(Plan.builder()
                    .code("FREE").name("Khởi Đầu")
                    .priceMonthly(BigDecimal.ZERO).priceYearly(BigDecimal.ZERO)
                    .maxUsers(3).maxWarehouses(1).maxSkus(200).maxAiRequests(20)
                    .apiAccess(false).trialDays(0)
                    .build());
        }
        if (planRepository.findById("TRIAL").isEmpty()) {
            planRepository.save(Plan.builder()
                    .code("TRIAL").name("Dùng Thử")
                    .priceMonthly(BigDecimal.ZERO).priceYearly(BigDecimal.ZERO)
                    .maxUsers(50).maxWarehouses(20).maxSkus(-1).maxAiRequests(500)
                    .apiAccess(true).trialDays(30)
                    .build());
        }
        if (planRepository.findById("PRO").isEmpty()) {
            planRepository.save(Plan.builder()
                    .code("PRO").name("Chuyên Nghiệp")
                    .priceMonthly(new BigDecimal("499000.00")).priceYearly(new BigDecimal("4790000.00"))
                    .maxUsers(50).maxWarehouses(20).maxSkus(-1).maxAiRequests(500)
                    .apiAccess(true).trialDays(0)
                    .build());
        }

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
            admin.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.ADMIN).orElseThrow())));
            admin.setWarehouseIds(new java.util.HashSet<>(Set.of(1L, 2L)));
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
            staff.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.WAREHOUSE_STAFF).orElseThrow())));
            staff.setWarehouseIds(new java.util.HashSet<>(Set.of(1L)));
            userRepository.save(staff);
        }

        // Seed tài khoản manager
        if (userRepository.findByUsername("manager").isEmpty()) {
            User manager = new User();
            manager.setUsername("manager");
            manager.setEmail("manager@wms.vn");
            manager.setPasswordHash(passwordEncoder.encode("Demopassword"));
            manager.setFullName("Manager User");
            manager.setEnabled(true);
            manager.setRoles(new java.util.HashSet<>(Set.of(roleRepository.findByName(Roles.WAREHOUSE_MANAGER).orElseThrow())));
            manager.setWarehouseIds(new java.util.HashSet<>(Set.of(1L, 2L)));
            userRepository.save(manager);
        }
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
