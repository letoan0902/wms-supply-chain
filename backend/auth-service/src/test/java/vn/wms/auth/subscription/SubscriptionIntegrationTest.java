package vn.wms.auth.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.dto.auth.LoginRequest;
import vn.wms.auth.entity.User;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.dto.RenewMockRequest;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.entity.SubscriptionStatus;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.auth.subscription.service.SubscriptionCacheWriter;
import vn.wms.auth.subscription.service.SubscriptionScheduler;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "payos.mock=true",
        "app.demo-tools=true"
})
public class SubscriptionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionScheduler subscriptionScheduler;

    @Test
    @DisplayName("API công khai GET /api/auth/plans trả về danh sách 3 gói dịch vụ")
    void testGetPublicPlans() throws Exception {
        mockMvc.perform(get("/api/auth/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[*].code", hasItems("FREE", "TRIAL", "PRO")));
    }

    @Test
    @DisplayName("Đăng ký doanh nghiệp mới thành công: tạo Company, Admin, Subscription TRIAL 30 ngày và đăng nhập được")
    void testSelfRegistrationSuccessAndLogin() throws Exception {
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("Công ty Cổ phần Vận Tải Anpha")
                .taxCode("0109988776")
                .phone("0988776655")
                .fullName("Trần Văn Anpha")
                .username("admin_anpha")
                .email("anpha@wms.vn")
                .password("Demopassword")
                .build();

        String regResponseStr = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.company.name").value("Công ty Cổ phần Vận Tải Anpha"))
                .andExpect(jsonPath("$.data.company.code", startsWith("CTY")))
                .andExpect(jsonPath("$.data.company.status").value("PENDING"))
                .andExpect(jsonPath("$.data.admin.username").value("admin_anpha"))
                .andExpect(jsonPath("$.data.subscription.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.admin.password").doesNotExist())
                .andExpect(jsonPath("$.data.admin.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode regData = objectMapper.readTree(regResponseStr).path("data");
        long companyId = regData.path("company").path("id").asLong();
        assertThat(regData.path("company").path("code").asText()).isEqualTo(String.format("CTY%06d", companyId));

        // Admin toàn hệ thống phê duyệt doanh nghiệp
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/auth/companies/" + companyId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.adminUser.enabled").value(true));

        // trial_ends_at = thời điểm phê duyệt + 30 ngày
        Subscription sub = subscriptionRepository.findByCompanyId(companyId).orElseThrow();
        assertThat(sub.getTrialEndsAt()).isBetween(
                Instant.now().plus(30, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES),
                Instant.now().plus(30, ChronoUnit.DAYS));

        // Kiểm tra Redis cache đã được đồng bộ đủ các trường của mục 2.7
        Map<String, String> redisData = mockRedisHashMap.get("wms:subscription:" + companyId);
        assertThat(redisData).isNotNull();
        assertThat(redisData.get("plan")).isEqualTo("TRIAL");
        assertThat(redisData.get("status")).isEqualTo("ACTIVE");
        assertThat(redisData).containsEntry("expiresAt", sub.getTrialEndsAt().toString())
                .containsEntry("maxUsers", "50")
                .containsEntry("maxWarehouses", "20")
                .containsEntry("maxSkus", "-1")
                .containsEntry("maxAiRequests", "500")
                .containsEntry("apiAccess", "true")
                .hasSize(8);

        // Đăng nhập bằng tài khoản Admin mới tạo sau khi đã được phê duyệt: đăng nhập cũng ghi lại bộ nhớ đệm
        mockRedisHashMap.clear();
        LoginRequest loginReq = new LoginRequest("admin_anpha", "Demopassword");
        String loginResponseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.company.id").value(companyId))
                .andExpect(jsonPath("$.data.subscription.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.subscription.expiresAt").value(sub.getTrialEndsAt().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("plan", "TRIAL").containsEntry("status", "ACTIVE");

        String token = objectMapper.readTree(loginResponseStr).path("data").path("accessToken").asText();
        Claims claims = jwtService.parse(token);
        assertThat(claims.get("plan", String.class)).isEqualTo("TRIAL");
        assertThat(jwtService.companyIdOf(claims)).isEqualTo(companyId);

        // /api/auth/me trả company và subscription
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.id").value(companyId))
                .andExpect(jsonPath("$.data.subscription.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"));

        // Gọi GET /api/auth/subscription
        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.planName").value("Dùng thử"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.readOnly").value(false))
                .andExpect(jsonPath("$.data.daysRemaining").value(greaterThanOrEqualTo(29)))
                .andExpect(jsonPath("$.data.daysRemaining").value(30))
                .andExpect(jsonPath("$.data.limits.maxUsers").value(50))
                .andExpect(jsonPath("$.data.limits.maxWarehouses").value(20))
                .andExpect(jsonPath("$.data.limits.maxSkus").value(-1))
                .andExpect(jsonPath("$.data.limits.maxAiRequests").value(500))
                .andExpect(jsonPath("$.data.limits.apiAccess").value(true))
                .andExpect(jsonPath("$.data.usage.users").value(1));
    }

    @Test
    @DisplayName("Đăng ký trùng tên đăng nhập hoặc email bị từ chối 409 CONFLICT và không sinh Company rác")
    void testRegistrationConflictRollback() throws Exception {
        long initialCompanyCount = companyRepository.count();

        // Đăng ký lần 1
        RegisterCompanyRequest req1 = RegisterCompanyRequest.builder()
                .companyName("Công ty Beta 1")
                .fullName("User Beta")
                .username("beta_unique_user")
                .email("beta@wms.vn")
                .password("Demopassword")
                .build();

        mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        long afterFirstCount = companyRepository.count();
        assertThat(afterFirstCount).isEqualTo(initialCompanyCount + 1);
        long subscriptionCount = subscriptionRepository.count();

        // Đăng ký lần 2 với cùng username -> Phải bị 409
        RegisterCompanyRequest reqDuplicateUsername = RegisterCompanyRequest.builder()
                .companyName("Công ty Beta 2 Trùng Tên")
                .fullName("User Beta Trùng")
                .username("beta_unique_user")
                .email("different_beta@wms.vn")
                .password("Demopassword")
                .build();

        mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDuplicateUsername)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("Tên đăng nhập")));

        // Đăng ký lần 3 với cùng email -> Phải bị 409
        RegisterCompanyRequest reqDuplicateEmail = RegisterCompanyRequest.builder()
                .companyName("Công ty Beta 3 Trùng Email")
                .fullName("User Beta Ba")
                .username("beta_other_user")
                .email("beta@wms.vn")
                .password("Demopassword")
                .build();

        mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDuplicateEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("Email")));

        // Kiểm tra số lượng Company trong DB không đổi (Transactional Rollback hoạt động)
        assertThat(companyRepository.count()).isEqualTo(afterFirstCount);
        assertThat(subscriptionRepository.count()).isEqualTo(subscriptionCount);
        assertThat(userRepository.findByUsername("beta_other_user")).isEmpty();
    }

    @Test
    @DisplayName("Đăng ký kiểm tra dữ liệu như tạo tài khoản và không gắn được vào doanh nghiệp đang có")
    void testRegistrationValidationAndNoAttachToExistingCompany() throws Exception {
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("companyName", "Công ty Sai Dữ Liệu");
        invalid.put("fullName", "Người Thử");
        invalid.put("username", "ten co dau cach");
        invalid.put("email", "invalid_user@wms.vn");
        invalid.put("password", "Demopassword");
        mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        // Body có companyId của Công ty mẫu A vẫn tạo doanh nghiệp mới, tài khoản không thuộc A
        Map<String, Object> body = new HashMap<>();
        body.put("companyName", "Công ty Epsilon");
        body.put("fullName", "User Epsilon");
        body.put("username", "admin_epsilon");
        body.put("email", "epsilon@wms.vn");
        body.put("password", "Demopassword");
        body.put("companyId", 1);
        String res = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(res).path("data").path("company").path("id").asLong();
        assertThat(companyId).isNotIn(1L, 2L);

        User created = userRepository.findByUsername("admin_epsilon").orElseThrow();
        assertThat(created.getCompany().getId()).isEqualTo(companyId);
        assertThat(created.getPasswordHash()).isNotEqualTo("Demopassword");
        assertThat(passwordEncoder.matches("Demopassword", created.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Gia hạn thử nghiệm (renew-mock) chuyển gói sang PRO và tăng ngày hết hạn")
    void testRenewMockProPlan() throws Exception {
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("Công ty Gamma Logistics")
                .fullName("User Gamma")
                .username("admin_gamma")
                .email("gamma@wms.vn")
                .password("Demopassword")
                .build();

        String reg = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(reg).path("data").path("company").path("id").asLong();
        approveCompany(companyId);
        Instant trialEndsAt = subscriptionRepository.findByCompanyId(companyId).orElseThrow().getTrialEndsAt();
        activateCompanyByEmail("gamma@wms.vn");

        String loginResponseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin_gamma", "Demopassword"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponseStr).path("data").path("accessToken").asText();

        // Gọi renew-mock với gói PRO theo tháng (30 ngày)
        RenewMockRequest renewReq = RenewMockRequest.builder()
                .plan("PRO")
                .cycle(BillingCycle.MONTHLY)
                .build();

        mockMvc.perform(post("/api/auth/subscription/renew-mock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Công thức mục 2.6: max(hiện tại, mốc dùng thử còn hiệu lực) + 30 ngày
        Instant firstExpiry = subscriptionRepository.findByCompanyId(companyId).orElseThrow().getExpiresAt();
        assertThat(firstExpiry).isEqualTo(trialEndsAt.plus(30, ChronoUnit.DAYS));
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("plan", "PRO")
                .containsEntry("status", "ACTIVE")
                .containsEntry("expiresAt", firstExpiry.toString())
                .containsEntry("maxSkus", "-1");

        // Kiểm tra lại subscription
        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("PRO"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.daysRemaining").value(greaterThanOrEqualTo(29)));

        // Gia hạn sớm theo năm: cộng 365 ngày vào mốc đang còn hiệu lực, không mất ngày còn lại
        mockMvc.perform(post("/api/auth/subscription/renew-mock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RenewMockRequest.builder().plan("PRO").cycle(BillingCycle.YEARLY).build())))
                .andExpect(status().isOk());
        Instant secondExpiry = subscriptionRepository.findByCompanyId(companyId).orElseThrow().getExpiresAt();
        assertThat(secondExpiry).isEqualTo(firstExpiry.plus(365, ChronoUnit.DAYS));
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("expiresAt", secondExpiry.toString());

        // Đang PRO còn hạn thì không được chuyển về FREE
        mockMvc.perform(post("/api/auth/subscription/downgrade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // renew-mock chỉ nhận gói PRO
        mockMvc.perform(post("/api/auth/subscription/renew-mock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RenewMockRequest.builder().plan("TRIAL").cycle(BillingCycle.MONTHLY).build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Demo hết hạn (demo/expire) và hạ về gói FREE (downgrade)")
    void testDemoExpireAndDowngrade() throws Exception {
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("Công ty Delta Express")
                .fullName("User Delta")
                .username("admin_delta")
                .email("delta@wms.vn")
                .password("Demopassword")
                .build();

        String reg = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(reg).path("data").path("company").path("id").asLong();
        approveCompany(companyId);

        String loginResponseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin_delta", "Demopassword"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponseStr).path("data").path("accessToken").asText();

        // 1. Kích hoạt demo/expire -> Trở thành EXPIRED và readOnly = true
        mockMvc.perform(post("/api/auth/subscription/demo/expire")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("plan", "TRIAL")
                .containsEntry("status", "EXPIRED");

        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.readOnly").value(true))
                .andExpect(jsonPath("$.data.daysRemaining").value(0));

        // 2. Hạ gói về FREE
        mockMvc.perform(post("/api/auth/subscription/downgrade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("plan", "FREE")
                .containsEntry("status", "ACTIVE")
                .containsEntry("expiresAt", "")
                .containsEntry("maxUsers", "3")
                .containsEntry("maxWarehouses", "1")
                .containsEntry("maxSkus", "200")
                .containsEntry("maxAiRequests", "20")
                .containsEntry("apiAccess", "false");

        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.readOnly").value(false))
                .andExpect(jsonPath("$.data.limits.maxUsers").value(3))
                .andExpect(jsonPath("$.data.limits.maxWarehouses").value(1))
                .andExpect(jsonPath("$.data.limits.maxSkus").value(200));

        // 3. Đang FREE thì chuyển về FREE lần nữa không hợp lệ
        mockMvc.perform(post("/api/auth/subscription/downgrade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Tác vụ định kỳ chuyển thuê bao quá hạn sang EXPIRED và ghi lại Redis")
    void testSchedulerExpiresDueSubscription() throws Exception {
        String reg = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterCompanyRequest.builder()
                                .companyName("Công ty Zeta")
                                .fullName("User Zeta")
                                .username("admin_zeta")
                                .email("zeta@wms.vn")
                                .password("Demopassword")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(reg).path("data").path("company").path("id").asLong();
        approveCompany(companyId);

        jdbcTemplate.update("UPDATE subscriptions SET trial_ends_at = ? WHERE company_id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), companyId);
        mockRedisHashMap.clear();

        subscriptionScheduler.scanAndExpireSubscriptions();

        assertThat(subscriptionRepository.findByCompanyId(companyId).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("plan", "TRIAL")
                .containsEntry("status", "EXPIRED");

        // Đăng nhập vẫn được khi đã hết hạn, kết quả báo EXPIRED
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin_zeta", "Demopassword"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("Đăng nhập chuyển EXPIRED thuê bao đã quá hạn khi tác vụ định kỳ chưa chạy")
    void testLoginExpiresDueSubscription() throws Exception {
        String reg = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterCompanyRequest.builder()
                                .companyName("Công ty Eta")
                                .fullName("User Eta")
                                .username("admin_eta")
                                .email("eta@wms.vn")
                                .password("Demopassword")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(reg).path("data").path("company").path("id").asLong();
        approveCompany(companyId);

        jdbcTemplate.update("UPDATE subscriptions SET trial_ends_at = ? WHERE company_id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), companyId);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin_eta", "Demopassword"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("EXPIRED"));

        // Trạng thái EXPIRED được lưu vào cơ sở dữ liệu, không chỉ ở Redis
        assertThat(subscriptionRepository.findByCompanyId(companyId).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(companyId)))
                .containsEntry("status", "EXPIRED");
    }

    @Test
    @DisplayName("API thuê bao chỉ dành cho người đã đăng nhập, thao tác ghi chỉ dành cho ADMIN")
    void testSubscriptionEndpointsRequireAuthAndAdmin() throws Exception {
        mockMvc.perform(get("/api/auth/subscription"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/subscription/downgrade"))
                .andExpect(status().isUnauthorized());
        // Quy tắc của WMS-22.2 cho /api/auth/company vẫn giữ: chưa đăng nhập trả 401
        mockMvc.perform(get("/api/auth/company"))
                .andExpect(status().isUnauthorized());

        String staffToken = loginAndGetToken("staff", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/auth/subscription/downgrade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/subscription/demo/expire")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    private void approveCompany(long companyId) throws Exception {
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/auth/companies/" + companyId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
