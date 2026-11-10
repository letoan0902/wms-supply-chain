package vn.wms.auth.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.dto.auth.LoginRequest;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.dto.RenewMockRequest;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.auth.subscription.service.SubscriptionScheduler;
import vn.wms.common.plan.SubscriptionCacheKeys;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giới hạn số người dùng theo gói và mức sử dụng trong {@code GET /api/auth/subscription} (WMS-23.2, phần 2).
 * PlanLimitChecker thật đọc hash {@code wms:subscription:{companyId}} mà SubscriptionCacheWriter ghi vào Redis giả
 * lập của {@link BaseIntegrationTest}. Mỗi test đăng ký một doanh nghiệp riêng để không lẫn dữ liệu.
 */
@TestPropertySource(properties = "payos.mock=true")
class UserPlanLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SubscriptionScheduler subscriptionScheduler;

    /** Doanh nghiệp vừa đăng ký và token của ADMIN đầu tiên. */
    private record Tenant(long companyId, String token) {
    }

    private Tenant registerCompany(String suffix) throws Exception {
        String admin = "admin_" + suffix;
        String reg = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterCompanyRequest.builder()
                                .companyName("Công ty " + suffix)
                                .fullName("Quản trị " + suffix)
                                .username(admin)
                                .email(admin + "@wms.vn")
                                .password(DEFAULT_PASSWORD)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(reg).path("data").path("company").path("id").asLong();

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(admin, DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tenant(companyId, objectMapper.readTree(login).path("data").path("accessToken").asText());
    }

    /** Doanh nghiệp đang dùng thử chuyển về gói Khởi Đầu (tối đa 3 người dùng). */
    private Tenant registerFreeCompany(String suffix) throws Exception {
        Tenant tenant = registerCompany(suffix);
        mockMvc.perform(post("/api/auth/subscription/downgrade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk());
        assertThat(mockRedisHashMap.get(SubscriptionCacheKeys.subscriptionKey(tenant.companyId())))
                .containsEntry("plan", "FREE").containsEntry("maxUsers", "3");
        return tenant;
    }

    private ResultActions createUser(Tenant tenant, String username) throws Exception {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", username + "@wms.vn",
                "password", DEFAULT_PASSWORD,
                "fullName", "Nhân viên " + username,
                "roles", Set.of("WAREHOUSE_STAFF"));
        return mockMvc.perform(post("/api/auth/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long createUserId(Tenant tenant, String username) throws Exception {
        String res = createUser(tenant, username)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data").path("id").asLong();
    }

    private ResultActions setEnabled(Tenant tenant, long userId, boolean enabled) throws Exception {
        return mockMvc.perform(patch("/api/auth/users/{id}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": " + enabled + "}"));
    }

    private String usersUsage(Tenant tenant) {
        return mockRedisHashMap.getOrDefault(SubscriptionCacheKeys.usageKey(tenant.companyId()), Map.of())
                .get(SubscriptionCacheKeys.USAGE_FIELD_USERS);
    }

    private static void expectUserLimitExceeded(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message")
                        .value("Gói Khởi Đầu chỉ cho phép tối đa 3 người dùng. Nâng cấp gói để thêm người dùng mới."))
                .andExpect(jsonPath("$.data.limit").value("USERS"))
                .andExpect(jsonPath("$.data.current").value(3))
                .andExpect(jsonPath("$.data.max").value(3))
                .andExpect(jsonPath("$.data.plan").value("FREE"));
    }

    @Test
    @DisplayName("Gói FREE: tài khoản thứ 4 bị chặn, khóa bớt một tài khoản thì tạo được, mở khóa vượt giới hạn bị chặn")
    void freePlanEnforcesUserLimitOnCreateAndReenable() throws Exception {
        Tenant tenant = registerFreeCompany("lim1");

        createUserId(tenant, "lim1_staff2");
        long third = createUserId(tenant, "lim1_staff3");
        assertThat(usersUsage(tenant)).isEqualTo("3");

        // Tài khoản thứ 4 qua cả hai API tạo tài khoản đều bị chặn, không lưu vào cơ sở dữ liệu
        expectUserLimitExceeded(createUser(tenant, "lim1_staff4"));
        expectUserLimitExceeded(mockMvc.perform(post("/api/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", "lim1_reg4",
                        "email", "lim1_reg4@wms.vn",
                        "password", DEFAULT_PASSWORD,
                        "fullName", "Nhân viên lim1_reg4",
                        "roles", Set.of("WAREHOUSE_STAFF"))))));
        assertThat(userRepository.findByUsername("lim1_staff4")).isEmpty();
        assertThat(userRepository.findByUsername("lim1_reg4")).isEmpty();

        // Khóa bớt một tài khoản thì còn chỗ cho tài khoản mới
        setEnabled(tenant, third, false).andExpect(status().isOk());
        assertThat(usersUsage(tenant)).isEqualTo("2");
        long fourth = createUserId(tenant, "lim1_staff4");
        assertThat(usersUsage(tenant)).isEqualTo("3");

        // Mở khóa lại tài khoản đã khóa tính là thêm người dùng: bị chặn qua PATCH trạng thái lẫn PUT cập nhật
        expectUserLimitExceeded(setEnabled(tenant, third, true));
        expectUserLimitExceeded(mockMvc.perform(put("/api/auth/users/{id}", third)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "lim1_staff3@wms.vn",
                        "roles", Set.of("WAREHOUSE_STAFF"),
                        "enabled", true)))));
        assertThat(userRepository.findById(third).orElseThrow().isEnabled()).isFalse();

        // Khóa tài khoản khác rồi mới mở khóa thì được
        setEnabled(tenant, fourth, false).andExpect(status().isOk());
        setEnabled(tenant, third, true).andExpect(status().isOk());
        assertThat(usersUsage(tenant)).isEqualTo("3");
    }

    @Test
    @DisplayName("Gói PRO cho phép nhiều hơn 3 người dùng")
    void proPlanAllowsMoreUsers() throws Exception {
        Tenant tenant = registerCompany("lim2");
        mockMvc.perform(post("/api/auth/subscription/renew-mock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RenewMockRequest.builder().plan("PRO").cycle(BillingCycle.MONTHLY).build())))
                .andExpect(status().isOk());

        for (int i = 2; i <= 5; i++) {
            createUser(tenant, "lim2_staff" + i).andExpect(status().isCreated());
        }
        assertThat(userRepository.countByCompanyIdAndEnabledTrue(tenant.companyId())).isEqualTo(5);
        assertThat(usersUsage(tenant)).isEqualTo("5");
    }

    @Test
    @DisplayName("Đăng ký doanh nghiệp mới không bị chặn và ghi số người dùng ban đầu")
    void companyRegistrationIsNotBlockedAndReportsUsage() throws Exception {
        // Doanh nghiệp khác đã chạm giới hạn của gói FREE không ảnh hưởng tới việc đăng ký doanh nghiệp mới
        registerFreeCompany("lim3a");
        Tenant tenant = registerCompany("lim3b");

        assertThat(userRepository.countByCompanyIdAndEnabledTrue(tenant.companyId())).isEqualTo(1);
        assertThat(usersUsage(tenant)).isEqualTo("1");
    }

    @Test
    @DisplayName("GET /api/auth/subscription trả đủ bốn số usage: người dùng từ cơ sở dữ liệu, còn lại từ Redis")
    void subscriptionReturnsAllUsageNumbers() throws Exception {
        Tenant tenant = registerCompany("lim4");
        createUser(tenant, "lim4_staff2").andExpect(status().isCreated());

        // Chưa service nào ghi số kho, SKU, lượt AI: kho và SKU là null, lượt AI là 0
        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usage.users").value(2))
                .andExpect(jsonPath("$.data.usage.warehouses").value(nullValue()))
                .andExpect(jsonPath("$.data.usage.skus").value(nullValue()))
                .andExpect(jsonPath("$.data.usage.aiRequests").value(0));

        Map<String, String> usage = mockRedisHashMap.computeIfAbsent(
                SubscriptionCacheKeys.usageKey(tenant.companyId()), k -> new HashMap<>());
        usage.put(SubscriptionCacheKeys.USAGE_FIELD_WAREHOUSES, "2");
        usage.put(SubscriptionCacheKeys.USAGE_FIELD_SKUS, "150");
        // Số người dùng trên Redis cũ hơn cơ sở dữ liệu thì vẫn trả số trong cơ sở dữ liệu
        usage.put(SubscriptionCacheKeys.USAGE_FIELD_USERS, "9");
        mockRedisStore.put(SubscriptionCacheKeys.aiUsageKey(tenant.companyId(),
                YearMonth.now(SubscriptionCacheKeys.BUSINESS_ZONE)), "7");

        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usage.users").value(2))
                .andExpect(jsonPath("$.data.usage.warehouses").value(2))
                .andExpect(jsonPath("$.data.usage.skus").value(150))
                .andExpect(jsonPath("$.data.usage.aiRequests").value(7));
    }

    @Test
    @DisplayName("GET /api/auth/subscription vẫn trả khi Redis lỗi, các số đọc từ Redis là null")
    void subscriptionUsageWhenRedisDown() throws Exception {
        Tenant tenant = registerCompany("lim5");
        doThrow(new RedisConnectionFailureException("Redis không chạy")).when(hashOperations).entries(anyString());

        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usage.users").value(1))
                .andExpect(jsonPath("$.data.usage.warehouses").value(nullValue()))
                .andExpect(jsonPath("$.data.usage.aiRequests").value(nullValue()));
    }

    @Test
    @DisplayName("Tác vụ định kỳ ghi lại bộ nhớ đệm của mọi thuê bao sau khi Redis mất dữ liệu")
    void schedulerRewritesCacheOfEverySubscription() throws Exception {
        Tenant trial = registerCompany("lim6a");
        Tenant free = registerFreeCompany("lim6b");

        // Giả lập Redis bị xóa sạch dữ liệu
        mockRedisHashMap.clear();

        subscriptionScheduler.scanAndExpireSubscriptions();

        assertThat(mockRedisHashMap.get(SubscriptionCacheKeys.subscriptionKey(trial.companyId())))
                .containsEntry("plan", "TRIAL").containsEntry("status", "ACTIVE").containsEntry("maxUsers", "50");
        assertThat(mockRedisHashMap.get(SubscriptionCacheKeys.subscriptionKey(free.companyId())))
                .containsEntry("plan", "FREE").containsEntry("status", "ACTIVE").containsEntry("expiresAt", "")
                .containsEntry("maxUsers", "3");
    }

    @Test
    @DisplayName("Tác vụ định kỳ không lỗi khi Redis không chạy")
    void schedulerSurvivesRedisOutage() throws Exception {
        registerCompany("lim7");
        doThrow(new RedisConnectionFailureException("Redis không chạy")).when(hashOperations).putAll(anyString(), any());

        assertThatCode(() -> subscriptionScheduler.scanAndExpireSubscriptions()).doesNotThrowAnyException();
    }
}
