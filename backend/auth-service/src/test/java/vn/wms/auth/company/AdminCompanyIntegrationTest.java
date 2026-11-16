package vn.wms.auth.company;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.service.AuthService;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.auth.subscription.service.SubscriptionCacheWriter;
import vn.wms.common.constant.Roles;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.AccountEmailKind;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Duyệt và từ chối bản đăng ký doanh nghiệp (docs/SAAS_SPEC.md mục 6): chỉ quản trị nền tảng ({@code SUPER_ADMIN})
 * gọi được {@code POST /api/auth/platform/companies/{id}/approve} và {@code .../reject}; ADMIN của doanh nghiệp nhận
 * 403; doanh nghiệp hệ thống PLATFORM trả 404; chỉ bản đăng ký PENDING được duyệt hoặc từ chối. Các API quản lý doanh
 * nghiệp cũ dưới {@code /api/auth/companies} (PR 88) không còn tồn tại.
 *
 * <p>Lớp test không chạy trong giao dịch để bộ nhớ đệm Redis giả lập (ghi sau khi commit) kiểm tra được. Bản đăng ký
 * dùng tên đăng nhập, email, mã số thuế ngẫu nhiên; doanh nghiệp PLATFORM và tài khoản SUPER_ADMIN do test tạo được
 * xóa sau mỗi test.
 */
class AdminCompanyIntegrationTest extends BaseIntegrationTest {

    private static final String PLATFORM = "/api/auth/platform/companies/";
    private static final String ROOT_USERNAME = "root_review";

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Company platformCompany;
    private boolean platformCreatedHere;
    private User rootAdmin;

    @BeforeEach
    void seedPlatformOperator() {
        if (roleRepository.findByName(Roles.SUPER_ADMIN).isEmpty()) {
            Role role = new Role();
            role.setName(Roles.SUPER_ADMIN);
            role.setDescription("Platform administrator");
            roleRepository.save(role);
        }
        platformCompany = companyRepository.findByCode(Company.PLATFORM_CODE).orElse(null);
        platformCreatedHere = platformCompany == null;
        if (platformCreatedHere) {
            Company company = new Company();
            company.setCode(Company.PLATFORM_CODE);
            company.setName("Quản trị nền tảng");
            company.setStatus(CompanyStatus.ACTIVE);
            company.setActivatedAt(Instant.now());
            platformCompany = companyRepository.save(company);
        }
        rootAdmin = userRepository.findByUsername(ROOT_USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(ROOT_USERNAME);
            user.setEmail(ROOT_USERNAME + "@wms.vn");
            user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            user.setFullName("Quản trị nền tảng");
            user.setEnabled(true);
            user.setCompany(platformCompany);
            user.setRoles(new HashSet<>(Set.of(roleRepository.findByName(Roles.SUPER_ADMIN).orElseThrow())));
            return userRepository.save(user);
        });
    }

    @AfterEach
    void removePlatformOperator() {
        userRepository.findByUsername(ROOT_USERNAME).ifPresent(userRepository::delete);
        if (platformCreatedHere) {
            companyRepository.deleteById(platformCompany.getId());
        }
    }

    // ==========================================
    // Phân quyền
    // ==========================================

    @Test
    @DisplayName("Duyệt và từ chối: chưa đăng nhập 401, ADMIN doanh nghiệp 403, doanh nghiệp vẫn PENDING")
    void approveAndRejectAreSuperAdminOnly() throws Exception {
        Registration reg = register();
        String companyAdmin = loginAndGetToken("admin", DEFAULT_PASSWORD);

        mockMvc.perform(approveRequest(reg.companyId(), null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(rejectRequest(reg.companyId(), "Không xác minh được"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(approveRequest(reg.companyId(), null)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(rejectRequest(reg.companyId(), "Không xác minh được")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(companyStatus(reg.companyId())).isEqualTo(CompanyStatus.PENDING);
        assertThat(accountEmailsTo(reg.email(), AccountEmailKind.COMPANY_APPROVED)).isEmpty();
        assertThat(accountEmailsTo(reg.email(), AccountEmailKind.COMPANY_REJECTED)).isEmpty();
    }

    @Test
    @DisplayName("API quản lý doanh nghiệp cũ dưới /api/auth/companies không còn tồn tại với ADMIN")
    void removedCompanyAdminEndpointsDoNotExist() throws Exception {
        Registration reg = register();
        String companyAdmin = loginAndGetToken("admin", DEFAULT_PASSWORD);
        List<MockHttpServletRequestBuilder> removed = List.of(
                get("/api/auth/companies"),
                get("/api/auth/companies").param("status", "PENDING"),
                get("/api/auth/companies/" + reg.companyId()),
                post("/api/auth/companies/" + reg.companyId() + "/approve"),
                post("/api/auth/companies/" + reg.companyId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Thử"))),
                patch("/api/auth/companies/" + companyB.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED"))));
        for (MockHttpServletRequestBuilder request : removed) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + companyAdmin))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        }
        assertThat(companyStatus(reg.companyId())).isEqualTo(CompanyStatus.PENDING);
        assertThat(companyStatus(companyB.getId())).isEqualTo(CompanyStatus.ACTIVE);
    }

    // ==========================================
    // Duyệt
    // ==========================================

    @Test
    @DisplayName("Duyệt không gửi thân: TRIAL 30 ngày từ lúc duyệt, Redis ghi lại, email đã duyệt, đăng nhập được")
    void approveWithDefaultTrial() throws Exception {
        Registration reg = register();
        mockRedisHashMap.clear();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        asSuperAdmin(approveRequest(reg.companyId(), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(reg.companyId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activatedAt").isNotEmpty());

        Company company = companyRepository.findById(reg.companyId()).orElseThrow();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getActivatedAt()).isNotNull();
        Subscription sub = subscriptionRepository.findByCompanyId(reg.companyId()).orElseThrow();
        assertThat(sub.getPlan().getCode()).isEqualTo("TRIAL");
        assertThat(sub.getTrialEndsAt()).isBetween(before.plus(30, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS));
        assertThat(countSubscriptionsOf(reg.companyId())).isEqualTo(1);
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(reg.companyId())))
                .containsEntry("plan", "TRIAL")
                .containsEntry("status", "ACTIVE")
                .containsEntry("expiresAt", sub.getTrialEndsAt().toString());

        // Email đã duyệt: nội dung cố định ở notification-service, liên kết tới trang đăng nhập dựng từ cấu hình
        List<PublishedEvent> approved = publishedEvents.stream()
                .filter(e -> WmsEvents.RoutingKeys.AUTH_COMPANY_APPROVED.equals(e.routingKey()))
                .toList();
        assertThat(approved).hasSize(1);
        assertThat(approved.get(0).companyId()).isEqualTo(reg.companyId());
        AccountEmailRequestedEvent email = (AccountEmailRequestedEvent) approved.get(0).payload();
        assertThat(email.kind()).isEqualTo(AccountEmailKind.COMPANY_APPROVED);
        assertThat(email.recipientEmail()).isEqualTo(reg.email());
        assertThat(email.link()).endsWith("/login").doesNotContain("token=");
        assertThat(email.expiresAt()).isNull();

        // Người đăng ký đăng nhập được với gói dùng thử
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(reg.username(), DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.id").value(reg.companyId()))
                .andExpect(jsonPath("$.data.subscription.plan").value("TRIAL"));

        // Liên kết kích hoạt cũ trong email đăng ký: báo đã hoạt động, không đổi thuê bao hay ngày kích hoạt
        activateCompanyByEmail(reg.email());
        Company afterLink = companyRepository.findById(reg.companyId()).orElseThrow();
        assertThat(afterLink.getActivatedAt()).isEqualTo(company.getActivatedAt());
        Subscription subAfterLink = subscriptionRepository.findByCompanyId(reg.companyId()).orElseThrow();
        assertThat(subAfterLink.getId()).isEqualTo(sub.getId());
        assertThat(subAfterLink.getTrialEndsAt()).isEqualTo(sub.getTrialEndsAt());
        assertThat(subAfterLink.getPlan().getCode()).isEqualTo("TRIAL");
    }

    @Test
    @DisplayName("Duyệt với số ngày dùng thử tùy chọn: hạn bằng lúc duyệt cộng đúng số ngày")
    void approveWithCustomTrialDays() throws Exception {
        Registration reg = register();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        asSuperAdmin(approveRequest(reg.companyId(), Map.of("plan", "TRIAL", "trialDays", 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan").value("TRIAL"));

        Subscription sub = subscriptionRepository.findByCompanyId(reg.companyId()).orElseThrow();
        assertThat(sub.getTrialEndsAt()).isBetween(before.plus(7, ChronoUnit.DAYS), Instant.now().plus(7, ChronoUnit.DAYS));
        assertThat(sub.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Duyệt với gói PRO năm: hạn 365 ngày từ lúc duyệt, không cộng dồn thời gian dùng thử")
    void approveWithPro() throws Exception {
        Registration reg = register();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        asSuperAdmin(approveRequest(reg.companyId(), Map.of("plan", "pro", "cycle", "yearly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan").value("PRO"))
                .andExpect(jsonPath("$.data.subscriptionStatus").value("ACTIVE"));

        Subscription sub = subscriptionRepository.findByCompanyId(reg.companyId()).orElseThrow();
        assertThat(sub.getPlan().getCode()).isEqualTo("PRO");
        assertThat(sub.getTrialEndsAt()).isNull();
        assertThat(sub.getExpiresAt()).isBetween(before.plus(365, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(reg.companyId())))
                .containsEntry("plan", "PRO")
                .containsEntry("expiresAt", sub.getExpiresAt().toString());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(reg.username(), DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.plan").value("PRO"));
    }

    @Test
    @DisplayName("Duyệt với dữ liệu sai trả 400 và không đổi gì: gói lạ, chu kỳ lạ, PRO thiếu chu kỳ, số ngày ngoài 1 đến 90")
    void approveRejectsInvalidInput() throws Exception {
        Registration reg = register();
        List<Map<String, Object>> invalidBodies = List.of(
                Map.of("plan", "GOLD"),
                Map.of("plan", "FREE"),
                Map.of("plan", "PRO"),
                Map.of("plan", "PRO", "cycle", "WEEKLY"),
                Map.of("plan", "PRO", "cycle", "MONTHLY", "trialDays", 10),
                Map.of("plan", "TRIAL", "cycle", "MONTHLY"),
                Map.of("trialDays", 0),
                Map.of("trialDays", 91));
        for (Map<String, Object> body : invalidBodies) {
            asSuperAdmin(approveRequest(reg.companyId(), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        assertThat(companyStatus(reg.companyId())).isEqualTo(CompanyStatus.PENDING);
        assertThat(accountEmailsTo(reg.email(), AccountEmailKind.COMPANY_APPROVED)).isEmpty();
    }

    @Test
    @DisplayName("Duyệt hai lần: lần hai 400, thuê bao và email không bị tạo lại")
    void approveTwiceIsBadRequest() throws Exception {
        Registration reg = register();
        asSuperAdmin(approveRequest(reg.companyId(), Map.of("trialDays", 14))).andExpect(status().isOk());
        Instant trialEndsAt = subscriptionRepository.findByCompanyId(reg.companyId()).orElseThrow().getTrialEndsAt();

        asSuperAdmin(approveRequest(reg.companyId(), Map.of("plan", "PRO", "cycle", "YEARLY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        asSuperAdmin(rejectRequest(reg.companyId(), "Đổi ý")).andExpect(status().isBadRequest());

        Subscription sub = subscriptionRepository.findByCompanyId(reg.companyId()).orElseThrow();
        assertThat(sub.getPlan().getCode()).isEqualTo("TRIAL");
        assertThat(sub.getTrialEndsAt()).isEqualTo(trialEndsAt);
        assertThat(companyStatus(reg.companyId())).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(accountEmailsTo(reg.email(), AccountEmailKind.COMPANY_APPROVED)).hasSize(1);
        assertThat(accountEmailsTo(reg.email(), AccountEmailKind.COMPANY_REJECTED)).isEmpty();
    }

    // ==========================================
    // Từ chối
    // ==========================================

    @Test
    @DisplayName("Từ chối: SUSPENDED, không đăng nhập được, liên kết kích hoạt hết dùng được, không duyệt lại được")
    void rejectThenApproveIsBadRequest() throws Exception {
        Registration reg = register();

        // Thiếu lý do hoặc lý do quá dài
        asSuperAdmin(rejectRequest(reg.companyId(), "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        asSuperAdmin(rejectRequest(reg.companyId(), "x".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(companyStatus(reg.companyId())).isEqualTo(CompanyStatus.PENDING);

        asSuperAdmin(rejectRequest(reg.companyId(), "Mã số thuế không khớp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        Company rejectedCompany = companyRepository.findById(reg.companyId()).orElseThrow();
        assertThat(rejectedCompany.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        assertThat(rejectedCompany.getActivatedAt()).isNull();

        // Email từ chối: không kèm lý do, liên kết tới trang chủ
        List<AccountEmailRequestedEvent> rejected = accountEmailsTo(reg.email(), AccountEmailKind.COMPANY_REJECTED);
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).link()).doesNotContain("token=");
        assertThat(rejected.get(0).toString()).doesNotContain("Mã số thuế không khớp");

        // Người đăng ký không đăng nhập được, thông báo doanh nghiệp bị tạm khóa
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(reg.username(), DEFAULT_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"))
                .andExpect(jsonPath("$.message").value(AuthService.SUSPENDED_MESSAGE));

        // Liên kết kích hoạt trong email đăng ký không còn dùng được, gửi lại cũng không phát liên kết mới
        mockMvc.perform(post("/api/auth/companies/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", latestTokenSentTo(reg.email(), AccountEmailKind.ACTIVATION)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(companyStatus(reg.companyId())).isEqualTo(CompanyStatus.SUSPENDED);

        // Không duyệt lại, không từ chối lại
        asSuperAdmin(approveRequest(reg.companyId(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        asSuperAdmin(rejectRequest(reg.companyId(), "Lần hai")).andExpect(status().isBadRequest());
        assertThat(companyStatus(reg.companyId())).isEqualTo(CompanyStatus.SUSPENDED);
        assertThat(accountEmailsTo(reg.email(), AccountEmailKind.COMPANY_APPROVED)).isEmpty();
    }

    // ==========================================
    // Doanh nghiệp hệ thống, ID lạ, doanh nghiệp đang hoạt động
    // ==========================================

    @Test
    @DisplayName("PLATFORM và ID không tồn tại trả 404; doanh nghiệp đang hoạt động không duyệt hoặc từ chối được")
    void platformUnknownAndActiveCompanies() throws Exception {
        long platformId = platformCompany.getId();
        asSuperAdmin(approveRequest(platformId, null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        asSuperAdmin(rejectRequest(platformId, "Thử")).andExpect(status().isNotFound());
        assertThat(companyStatus(platformId)).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(subscriptionRepository.findByCompanyId(platformId)).isEmpty();

        asSuperAdmin(approveRequest(9_999_999L, null)).andExpect(status().isNotFound());
        asSuperAdmin(rejectRequest(9_999_999L, "Thử")).andExpect(status().isNotFound());

        asSuperAdmin(approveRequest(companyB.getId(), null)).andExpect(status().isBadRequest());
        asSuperAdmin(rejectRequest(companyB.getId(), "Thử")).andExpect(status().isBadRequest());
        assertThat(companyStatus(companyB.getId())).isEqualTo(CompanyStatus.ACTIVE);
    }

    // ==========================================
    // Hỗ trợ
    // ==========================================

    private record Registration(long companyId, String username, String email) {
    }

    /** Đăng ký một doanh nghiệp mới qua API công khai (trạng thái PENDING, có email kích hoạt). */
    private Registration register() throws Exception {
        long suffix = ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
        String username = "duyet_" + suffix;
        String email = username + "@vidu.vn";
        RegisterCompanyRequest request = RegisterCompanyRequest.builder()
                .companyName("Công ty Duyệt " + suffix)
                .taxCode("0" + suffix)
                .phone("0909123456")
                .fullName("Người đăng ký " + suffix)
                .username(username)
                .email(email)
                .password(DEFAULT_PASSWORD)
                .build();
        String body = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.company.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");
        return new Registration(data.path("company").path("id").asLong(), username, email);
    }

    private MockHttpServletRequestBuilder approveRequest(long companyId, Map<String, Object> body) throws Exception {
        MockHttpServletRequestBuilder request = post(PLATFORM + companyId + "/approve");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return request;
    }

    private MockHttpServletRequestBuilder rejectRequest(long companyId, String reason) throws Exception {
        return post(PLATFORM + companyId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason)));
    }

    private ResultActions asSuperAdmin(MockHttpServletRequestBuilder request) throws Exception {
        String token = jwtService.generateAccessToken(rootAdmin.getId(), ROOT_USERNAME, platformCompany.getId(),
                Company.PLATFORM_CODE, null, List.of(Roles.SUPER_ADMIN), List.of());
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private CompanyStatus companyStatus(long companyId) {
        return companyRepository.findById(companyId).orElseThrow().getStatus();
    }

    private long countSubscriptionsOf(long companyId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE company_id = ?",
                Long.class, companyId);
        return count == null ? 0 : count;
    }
}
