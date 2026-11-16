package vn.wms.auth.platform;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.service.AuthService;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.common.constant.Roles;
import vn.wms.common.plan.SubscriptionCacheKeys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử quản trị nền tảng (docs/SAAS_SPEC.md mục 6): phân quyền năm API, số liệu tổng quan, lọc danh sách doanh
 * nghiệp và thanh toán, tạm khóa và mở khóa, doanh nghiệp hệ thống PLATFORM không bao giờ lộ ra, và tài khoản
 * SUPER_ADMIN gọi API của doanh nghiệp nhận 403 hoặc 404 sạch.
 *
 * <p>Lớp test chạy trong một giao dịch rollback sau mỗi test. Cơ sở dữ liệu H2 dùng chung với các lớp test khác nên
 * trước mỗi test toàn bộ doanh nghiệp, tài khoản, thuê bao, thanh toán được xóa (chỉ trong giao dịch của test) rồi dựng
 * một bộ dữ liệu đã biết; Flyway không chạy nên test tự tạo vai trò SUPER_ADMIN và doanh nghiệp PLATFORM.
 *
 * <p>Bộ dữ liệu (tháng tính theo giờ Việt Nam, "m-2" là hai tháng trước tháng hiện tại):
 * <ul>
 *   <li>PLATFORM (ACTIVE, tạo tháng này): root_admin (SUPER_ADMIN); một thanh toán PAID 1.000 đồng không được tính</li>
 *   <li>Alpha CTY900001 (ACTIVE, tạo tháng này, TRIAL hết hạn sau 3 ngày): alpha_admin (ADMIN), alpha_staff (khóa)</li>
 *   <li>Beta CTY900002 (ACTIVE, tạo m-2, PRO còn 60 ngày): beta_admin (ADMIN)</li>
 *   <li>Gamma CTY900003 (PENDING, tạo m-2, chưa có thuê bao): gamma_admin (ADMIN)</li>
 *   <li>Delta CTY900004 (SUSPENDED, tạo 7 tháng trước, PRO EXPIRED): delta_admin (ADMIN)</li>
 *   <li>Epsilon CTY900005 (ACTIVE, tạo m-5, FREE): không có tài khoản</li>
 *   <li>Thanh toán: Alpha PAID 499.000 tháng này; Beta PAID 4.790.000 tháng m-2, PENDING quá hạn và CANCELLED tháng
 *       này; Delta PAID 499.000 bảy tháng trước</li>
 * </ul>
 */
@Transactional
class PlatformAdminIntegrationTest extends BaseIntegrationTest {

    private static final ZoneId VN = SubscriptionCacheKeys.BUSINESS_ZONE;
    private static final String PLATFORM = "/api/auth/platform";

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private long platformId;
    private long alphaId;
    private long betaId;
    private long gammaId;
    private long deltaId;
    private long epsilonId;
    private long rootAdminId;
    private long alphaAdminId;
    private Instant now;

    @BeforeEach
    void seedKnownDataSet() {
        entityManager.flush();
        for (String table : List.of("account_tokens", "api_keys", "payments", "subscriptions", "user_roles",
                "user_warehouses", "users", "companies")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        entityManager.clear();

        if (roleRepository.findByName(Roles.SUPER_ADMIN).isEmpty()) {
            Role role = new Role();
            role.setName(Roles.SUPER_ADMIN);
            role.setDescription("Platform administrator");
            roleRepository.save(role);
        }

        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant m2 = monthsAgo(2);
        Instant m5 = monthsAgo(5);
        Instant m7 = monthsAgo(7);

        platformId = insertCompany(Company.PLATFORM_CODE, "Quản trị nền tảng", null, null, "ACTIVE", now);
        alphaId = insertCompany("CTY900001", "Công ty Alpha", "0101111111", "lienhe@alpha.vn", "ACTIVE", now);
        betaId = insertCompany("CTY900002", "Công ty Beta", "0102222222", "lienhe@beta.vn", "ACTIVE", m2);
        gammaId = insertCompany("CTY900003", "Công ty Gamma", null, "lienhe@gamma.vn", "PENDING", m2.plusSeconds(60));
        deltaId = insertCompany("CTY900004", "Công ty Delta", null, "lienhe@delta.vn", "SUSPENDED", m7);
        epsilonId = insertCompany("CTY900005", "Công ty Epsilon", null, "lienhe@epsilon.vn", "ACTIVE", m5);
        jdbcTemplate.update("UPDATE companies SET address = ? WHERE id = ?", "1 Tràng Tiền, Hà Nội", alphaId);

        insertSubscription(alphaId, "TRIAL", "ACTIVE", now.plus(3, ChronoUnit.DAYS), null);
        insertSubscription(betaId, "PRO", "ACTIVE", null, now.plus(60, ChronoUnit.DAYS));
        insertSubscription(deltaId, "PRO", "EXPIRED", null, m7.plus(30, ChronoUnit.DAYS));
        insertSubscription(epsilonId, "FREE", "ACTIVE", null, null);

        rootAdminId = createUser("root_admin", platformId, Roles.SUPER_ADMIN, true);
        alphaAdminId = createUser("alpha_admin", alphaId, Roles.ADMIN, true);
        createUser("alpha_staff", alphaId, Roles.WAREHOUSE_STAFF, false);
        createUser("beta_admin", betaId, Roles.ADMIN, true);
        createUser("gamma_admin", gammaId, Roles.ADMIN, true);
        createUser("delta_admin", deltaId, Roles.ADMIN, true);

        insertPayment(alphaId, 1001L, "MONTHLY", 499_000L, "PAID", now, now);
        insertPayment(betaId, 1002L, "YEARLY", 4_790_000L, "PAID", m2, m2);
        insertPayment(betaId, 1003L, "MONTHLY", 499_000L, "PENDING", now.minusSeconds(7200), null);
        insertPayment(betaId, 1004L, "MONTHLY", 499_000L, "CANCELLED", now.minusSeconds(3600), null);
        insertPayment(deltaId, 1005L, "MONTHLY", 499_000L, "PAID", m7, m7);
        insertPayment(platformId, 1006L, "MONTHLY", 1_000L, "PAID", now, now);
        entityManager.clear();
    }

    // ==========================================
    // Phân quyền: 401, 403, 200
    // ==========================================

    @Test
    @DisplayName("Năm API nền tảng: chưa đăng nhập 401, ADMIN doanh nghiệp và nhân viên 403, SUPER_ADMIN 200")
    void everyPlatformEndpointIsSuperAdminOnly() throws Exception {
        String companyAdmin = tokenOf(alphaAdminId, "alpha_admin", alphaId, "CTY900001", Roles.ADMIN);
        String staff = tokenOf(alphaAdminId + 1, "alpha_staff", alphaId, "CTY900001", Roles.WAREHOUSE_STAFF);
        String superAdmin = superAdminToken();

        for (MockHttpServletRequestBuilder request : platformRequests()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        for (MockHttpServletRequestBuilder request : platformRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + companyAdmin))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        for (MockHttpServletRequestBuilder request : platformRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + staff))
                    .andExpect(status().isForbidden());
        }
        // Không có gì bị đổi bởi các lần gọi bị từ chối
        assertThat(companyRepository.findById(epsilonId).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");

        for (MockHttpServletRequestBuilder request : platformRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==========================================
    // Tổng quan
    // ==========================================

    @Test
    @DisplayName("Tổng quan: đếm đúng, bỏ PLATFORM, 6 tháng có cả tháng 0, doanh thu chỉ tính PAID")
    void overviewNumbersMatchKnownDataSet() throws Exception {
        YearMonth current = YearMonth.now(VN);
        String res = asSuperAdmin(get(PLATFORM + "/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companies.total").value(5))
                .andExpect(jsonPath("$.data.companies.active").value(3))
                .andExpect(jsonPath("$.data.companies.pending").value(1))
                .andExpect(jsonPath("$.data.companies.suspended").value(1))
                .andExpect(jsonPath("$.data.subscriptions.free").value(1))
                .andExpect(jsonPath("$.data.subscriptions.trial").value(1))
                .andExpect(jsonPath("$.data.subscriptions.pro").value(2))
                .andExpect(jsonPath("$.data.subscriptions.expired").value(1))
                .andExpect(jsonPath("$.data.subscriptions.expiringSoon").value(1))
                // root_admin của PLATFORM không được đếm; alpha_staff đang khóa
                .andExpect(jsonPath("$.data.users.total").value(5))
                .andExpect(jsonPath("$.data.users.active").value(4))
                // 499.000 + 4.790.000 + 499.000; không tính PENDING, CANCELLED, thanh toán của PLATFORM
                .andExpect(jsonPath("$.data.revenue.total").value(5_788_000))
                .andExpect(jsonPath("$.data.revenue.thisMonth").value(499_000))
                .andExpect(jsonPath("$.data.revenue.paidCount").value(3))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(res).path("data");
        List<String> expectedMonths = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            expectedMonths.add(current.minusMonths(i).toString());
        }
        JsonNode registrations = data.path("registrationsByMonth");
        JsonNode revenue = data.path("revenueByMonth");
        assertThat(registrations).hasSize(6);
        assertThat(revenue).hasSize(6);
        long[] expectedRegistrations = {1, 0, 0, 2, 0, 1};
        long[] expectedRevenue = {0, 0, 0, 4_790_000, 0, 499_000};
        for (int i = 0; i < 6; i++) {
            assertThat(registrations.get(i).path("month").asText()).isEqualTo(expectedMonths.get(i));
            assertThat(registrations.get(i).path("count").asLong()).isEqualTo(expectedRegistrations[i]);
            assertThat(revenue.get(i).path("month").asText()).isEqualTo(expectedMonths.get(i));
            assertThat(revenue.get(i).path("amount").asLong()).isEqualTo(expectedRevenue[i]);
        }

        // 5 doanh nghiệp mới nhất, không có PLATFORM; 3 thanh toán PAID của khách hàng, mới thanh toán trước
        List<String> recentCodes = new ArrayList<>();
        data.path("recentCompanies").forEach(c -> recentCodes.add(c.path("code").asText()));
        assertThat(recentCodes).containsExactly("CTY900001", "CTY900003", "CTY900002", "CTY900005", "CTY900004");
        List<Long> recentOrders = new ArrayList<>();
        data.path("recentPayments").forEach(p -> recentOrders.add(p.path("orderCode").asLong()));
        assertThat(recentOrders).containsExactly(1001L, 1002L, 1005L);
        assertThat(data.path("recentPayments").get(0).path("companyName").asText()).isEqualTo("Công ty Alpha");
    }

    // ==========================================
    // Danh sách và chi tiết doanh nghiệp
    // ==========================================

    @Test
    @DisplayName("Danh sách doanh nghiệp: lọc từ khóa, trạng thái, gói, phân trang; PLATFORM không bao giờ có mặt")
    void companyListFiltersAndPaging() throws Exception {
        String res = asSuperAdmin(get(PLATFORM + "/companies").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content[*].code", not(hasItem(Company.PLATFORM_CODE))))
                .andExpect(jsonPath("$.data.content[0].code").value("CTY900001"))
                .andExpect(jsonPath("$.data.content[0].plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.content[0].planName").value("Dùng thử"))
                .andExpect(jsonPath("$.data.content[0].subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[0].userCount").value(2))
                .andExpect(jsonPath("$.data.content[0].taxCode").value("0101111111"))
                .andExpect(jsonPath("$.data.content[0].address").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode alpha = objectMapper.readTree(res).path("data").path("content").get(0);
        assertThat(Instant.parse(alpha.path("expiresAt").asText())).isEqualTo(now.plus(3, ChronoUnit.DAYS));

        // Gamma chưa có thuê bao: plan, planName, subscriptionStatus, expiresAt đều null
        asSuperAdmin(get(PLATFORM + "/companies").param("keyword", "GAMMA"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.content[0].plan").isEmpty())
                .andExpect(jsonPath("$.data.content[0].planName").isEmpty())
                .andExpect(jsonPath("$.data.content[0].subscriptionStatus").isEmpty())
                .andExpect(jsonPath("$.data.content[0].expiresAt").isEmpty());

        // Từ khóa tìm trong mã, mã số thuế, email
        asSuperAdmin(get(PLATFORM + "/companies").param("keyword", "cty900002"))
                .andExpect(jsonPath("$.data.content[0].name").value("Công ty Beta"));
        asSuperAdmin(get(PLATFORM + "/companies").param("keyword", "0101111111"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("CTY900001"));
        asSuperAdmin(get(PLATFORM + "/companies").param("keyword", "@delta.vn"))
                .andExpect(jsonPath("$.data.content[0].code").value("CTY900004"));
        // Tìm theo tên hoặc mã của doanh nghiệp hệ thống không ra gì
        asSuperAdmin(get(PLATFORM + "/companies").param("keyword", "nền tảng"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        asSuperAdmin(get(PLATFORM + "/companies").param("keyword", "platform"))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        asSuperAdmin(get(PLATFORM + "/companies").param("status", "SUSPENDED"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("CTY900004"));
        asSuperAdmin(get(PLATFORM + "/companies").param("status", "ACTIVE"))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        asSuperAdmin(get(PLATFORM + "/companies").param("plan", "PRO"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        asSuperAdmin(get(PLATFORM + "/companies").param("plan", "free"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("CTY900005"));
        asSuperAdmin(get(PLATFORM + "/companies").param("plan", "PRO").param("status", "ACTIVE"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("CTY900002"));
        asSuperAdmin(get(PLATFORM + "/companies").param("plan", "VIP"))
                .andExpect(status().isBadRequest());
        asSuperAdmin(get(PLATFORM + "/companies").param("status", "DELETED"))
                .andExpect(status().isBadRequest());

        // Phân trang: mặc định 10 phần tử, trang bắt đầu từ 0
        asSuperAdmin(get(PLATFORM + "/companies"))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.page").value(0));
        asSuperAdmin(get(PLATFORM + "/companies").param("size", "2"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));
        asSuperAdmin(get(PLATFORM + "/companies").param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("CTY900004"))
                .andExpect(jsonPath("$.data.last").value(true));
        asSuperAdmin(get(PLATFORM + "/companies").param("size", "1000"))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("Chi tiết doanh nghiệp: giới hạn, mức dùng từ Redis, ADMIN, thanh toán; PLATFORM và ID lạ trả 404")
    void companyDetail() throws Exception {
        mockRedisHashMap.put(SubscriptionCacheKeys.usageKey(alphaId),
                new java.util.HashMap<>(Map.of(SubscriptionCacheKeys.USAGE_FIELD_WAREHOUSES, "2",
                        SubscriptionCacheKeys.USAGE_FIELD_SKUS, "37")));
        mockRedisStore.put(SubscriptionCacheKeys.aiUsageKey(alphaId, YearMonth.now(VN)), "5");

        asSuperAdmin(get(PLATFORM + "/companies/" + alphaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company.code").value("CTY900001"))
                .andExpect(jsonPath("$.data.company.address").value("1 Tràng Tiền, Hà Nội"))
                .andExpect(jsonPath("$.data.company.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.company.userCount").value(2))
                .andExpect(jsonPath("$.data.limits.maxUsers").value(50))
                .andExpect(jsonPath("$.data.limits.maxSkus").value(-1))
                .andExpect(jsonPath("$.data.limits.apiAccess").value(true))
                .andExpect(jsonPath("$.data.usage.users").value(1))
                .andExpect(jsonPath("$.data.usage.warehouses").value(2))
                .andExpect(jsonPath("$.data.usage.skus").value(37))
                .andExpect(jsonPath("$.data.usage.aiRequests").value(5))
                .andExpect(jsonPath("$.data.admins.length()").value(1))
                .andExpect(jsonPath("$.data.admins[0].username").value("alpha_admin"))
                .andExpect(jsonPath("$.data.admins[0].enabled").value(true))
                .andExpect(jsonPath("$.data.admins[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].orderCode").value(1001))
                .andExpect(jsonPath("$.data.payments[0].companyCode").value("CTY900001"));

        // Chưa có thuê bao: limits, usage null
        asSuperAdmin(get(PLATFORM + "/companies/" + gammaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limits").isEmpty())
                .andExpect(jsonPath("$.data.usage").isEmpty())
                .andExpect(jsonPath("$.data.admins[0].username").value("gamma_admin"));

        asSuperAdmin(get(PLATFORM + "/companies/" + platformId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        asSuperAdmin(get(PLATFORM + "/companies/" + (epsilonId + 1000)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Chi tiết doanh nghiệp: Redis lỗi thì số kho, SKU, lượt AI là null, API vẫn 200")
    void companyDetailWithUnreadableUsage() throws Exception {
        when(hashOperations.entries(SubscriptionCacheKeys.usageKey(betaId)))
                .thenThrow(new IllegalStateException("Redis không chạy"));

        asSuperAdmin(get(PLATFORM + "/companies/" + betaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usage.users").value(1))
                .andExpect(jsonPath("$.data.usage.warehouses").isEmpty())
                .andExpect(jsonPath("$.data.usage.skus").isEmpty())
                .andExpect(jsonPath("$.data.usage.aiRequests").isEmpty());
    }

    // ==========================================
    // Tạm khóa, mở khóa
    // ==========================================

    @Test
    @DisplayName("Tạm khóa chặn đăng nhập và làm mới token với thông báo của hợp đồng; mở khóa đăng nhập lại được")
    void suspendBlocksLoginAndRefreshThenUnlockRestores() throws Exception {
        JsonNode session = loginData("beta_admin");

        asSuperAdmin(put(PLATFORM + "/companies/" + betaId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED", "reason", "Nợ phí"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(betaId))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.plan").value("PRO"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("beta_admin", DEFAULT_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"))
                .andExpect(jsonPath("$.message").value(AuthService.SUSPENDED_MESSAGE));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", session.path("refreshToken").asText()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"))
                .andExpect(jsonPath("$.message").value("Doanh nghiệp đã bị tạm khóa. Vui lòng liên hệ quản trị nền tảng."));

        // Doanh nghiệp khác không bị ảnh hưởng
        loginData("alpha_admin");

        asSuperAdmin(put(PLATFORM + "/companies/" + betaId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        loginData("beta_admin");
    }

    @Test
    @DisplayName("Chuyển trạng thái không hợp lệ trả 400, PENDING không kích hoạt được, PLATFORM trả 404")
    void invalidStatusTransitions() throws Exception {
        // Đang SUSPENDED thì không tạm khóa lại; đang ACTIVE thì không mở khóa
        putStatus(deltaId, "SUSPENDED").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        putStatus(alphaId, "ACTIVE").andExpect(status().isBadRequest());
        // PENDING: không kích hoạt, không tạm khóa bằng API này
        putStatus(gammaId, "ACTIVE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
        putStatus(gammaId, "SUSPENDED").andExpect(status().isBadRequest());
        assertThat(companyRepository.findById(gammaId).orElseThrow().getStatus().name()).isEqualTo("PENDING");
        // Không chuyển sang PENDING, giá trị lạ, thiếu trạng thái
        putStatus(alphaId, "PENDING").andExpect(status().isBadRequest());
        putStatus(alphaId, "DELETED").andExpect(status().isBadRequest());
        asSuperAdmin(put(PLATFORM + "/companies/" + alphaId + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // Lý do quá 255 ký tự
        asSuperAdmin(put(PLATFORM + "/companies/" + alphaId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED", "reason", "x".repeat(256)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(companyRepository.findById(alphaId).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        // Doanh nghiệp hệ thống và ID không tồn tại
        putStatus(platformId, "SUSPENDED").andExpect(status().isNotFound());
        putStatus(epsilonId + 1000, "SUSPENDED").andExpect(status().isNotFound());
        assertThat(companyRepository.findById(platformId).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
    }

    // ==========================================
    // Thanh toán
    // ==========================================

    @Test
    @DisplayName("Danh sách thanh toán: lọc trạng thái, doanh nghiệp, ngày tạo; bỏ PLATFORM; PENDING quá hạn thành EXPIRED")
    void paymentListFilters() throws Exception {
        String res = asSuperAdmin(get(PLATFORM + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content[*].orderCode", not(hasItem(1006))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode first = objectMapper.readTree(res).path("data").path("content").get(0);
        // Mới tạo trước
        assertThat(first.path("orderCode").asLong()).isEqualTo(1001L);
        assertThat(first.path("companyCode").asText()).isEqualTo("CTY900001");
        assertThat(first.path("companyName").asText()).isEqualTo("Công ty Alpha");
        assertThat(first.path("plan").asText()).isEqualTo("PRO");
        assertThat(first.path("cycle").asText()).isEqualTo("MONTHLY");
        assertThat(first.path("amount").asLong()).isEqualTo(499_000L);
        assertThat(first.has("checkoutUrl")).isFalse();
        assertThat(first.has("qrCode")).isFalse();

        asSuperAdmin(get(PLATFORM + "/payments").param("status", "PAID"))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        // Đơn PENDING đã quá hạn được chuyển EXPIRED như lịch sử của doanh nghiệp
        asSuperAdmin(get(PLATFORM + "/payments").param("status", "PENDING"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        asSuperAdmin(get(PLATFORM + "/payments").param("status", "EXPIRED"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderCode").value(1003));
        asSuperAdmin(get(PLATFORM + "/payments").param("companyId", String.valueOf(betaId)))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        asSuperAdmin(get(PLATFORM + "/payments").param("companyId", String.valueOf(platformId)))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        LocalDate today = LocalDate.now(VN);
        LocalDate m2 = LocalDate.ofInstant(monthsAgo(2), VN);
        asSuperAdmin(get(PLATFORM + "/payments").param("from", m2.toString()).param("to", m2.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderCode").value(1002));
        asSuperAdmin(get(PLATFORM + "/payments").param("from", m2.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(4));
        asSuperAdmin(get(PLATFORM + "/payments").param("to", m2.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        asSuperAdmin(get(PLATFORM + "/payments").param("from", today.toString()).param("to", m2.toString()))
                .andExpect(status().isBadRequest());
        asSuperAdmin(get(PLATFORM + "/payments").param("from", "16/11/2026"))
                .andExpect(status().isBadRequest());
        asSuperAdmin(get(PLATFORM + "/payments").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    // ==========================================
    // Mục 4: SUPER_ADMIN đăng nhập và gọi API của doanh nghiệp
    // ==========================================

    @Test
    @DisplayName("SUPER_ADMIN đăng nhập và làm mới token khi PLATFORM không có thuê bao; token đúng hợp đồng")
    void superAdminLoginAndRefreshWithoutSubscription() throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("root_admin", DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value(Roles.SUPER_ADMIN))
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.company.code").value(Company.PLATFORM_CODE))
                .andExpect(jsonPath("$.data.subscription").isEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(res).path("data");
        Claims claims = jwtService.parse(data.path("accessToken").asText());
        assertThat(jwtService.companyIdOf(claims)).isEqualTo(platformId);
        assertThat(jwtService.companyCodeOf(claims)).isEqualTo(Company.PLATFORM_CODE);
        assertThat(jwtService.rolesOf(claims)).containsExactly(Roles.SUPER_ADMIN);
        assertThat(claims.get("warehouseIds", List.class)).isEmpty();
        assertThat(jwtService.planOf(claims)).isNull();
        // Không ghi bộ nhớ đệm thuê bao cho PLATFORM
        assertThat(mockRedisHashMap).doesNotContainKey(SubscriptionCacheKeys.subscriptionKey(platformId));

        String refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", data.path("refreshToken").asText()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Claims refreshedClaims = jwtService.parse(
                objectMapper.readTree(refreshed).path("data").path("accessToken").asText());
        assertThat(jwtService.rolesOf(refreshedClaims)).containsExactly(Roles.SUPER_ADMIN);
        assertThat(jwtService.companyIdOf(refreshedClaims)).isEqualTo(platformId);

        // Hồ sơ cá nhân và /me vẫn dùng được
        String token = data.path("accessToken").asText();
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value(Roles.SUPER_ADMIN))
                .andExpect(jsonPath("$.data.company.code").value(Company.PLATFORM_CODE))
                .andExpect(jsonPath("$.data.subscription").isEmpty());
        mockMvc.perform(get("/api/auth/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("root_admin"));
    }

    @Test
    @DisplayName("SUPER_ADMIN gọi API chỉ dành cho doanh nghiệp nhận 403 hoặc 404, không bao giờ 500")
    void companyOnlyEndpointsAnswerSuperAdminCleanly() throws Exception {
        String token = loginData("root_admin").path("accessToken").asText();

        expectStatus(get("/api/auth/subscription"), token, 404);
        expectStatus(get("/api/auth/payments"), token, 403);
        expectStatus(get("/api/auth/payments/1001"), token, 403);
        expectStatus(get("/api/auth/api-keys"), token, 403);
        expectStatus(get("/api/auth/users"), token, 403);
        expectStatus(get("/api/auth/users/" + alphaAdminId), token, 403);
        expectStatus(get("/api/auth/company"), token, 403);
        expectStatus(put("/api/auth/company").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Đổi tên"))), token, 403);
        expectStatus(post("/api/auth/subscription/downgrade"), token, 403);
        expectStatus(post("/api/auth/payments").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("plan", "PRO", "cycle", "MONTHLY"))), token, 403);
        expectStatus(post("/api/auth/api-keys").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Khóa"))), token, 403);
        expectStatus(post("/api/auth/users").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserBody("new_user", Roles.ADMIN))), token, 403);
        expectStatus(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserBody("new_user2", Roles.ADMIN))), token, 403);

        // Không có tài khoản hay thuê bao nào được tạo cho PLATFORM
        assertThat(subscriptionRepository.findByCompanyId(platformId)).isEmpty();
        assertThat(userRepository.findAllByCompanyId(platformId)).hasSize(1);
    }

    // ==========================================
    // Admin của doanh nghiệp và vai trò SUPER_ADMIN
    // ==========================================

    @Test
    @DisplayName("ADMIN doanh nghiệp không tạo, sửa, gán SUPER_ADMIN và không thấy tài khoản của PLATFORM")
    void companyAdminCannotTouchSuperAdmin() throws Exception {
        String token = loginData("alpha_admin").path("accessToken").asText();

        mockMvc.perform(post("/api/auth/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserBody("sneaky", Roles.SUPER_ADMIN))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vai trò không hợp lệ: SUPER_ADMIN"));
        mockMvc.perform(post("/api/auth/register").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserBody("sneaky2", Roles.SUPER_ADMIN))))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findByUsername("sneaky")).isEmpty();
        assertThat(userRepository.findByUsername("sneaky2")).isEmpty();

        long staffId = userRepository.findByUsername("alpha_staff").orElseThrow().getId();
        mockMvc.perform(put("/api/auth/users/" + staffId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "alpha_staff@wms.vn",
                                "roles", Set.of(Roles.WAREHOUSE_STAFF, Roles.SUPER_ADMIN)))))
                .andExpect(status().isBadRequest());
        User staff = userRepository.findById(staffId).orElseThrow();
        assertThat(staff.getRoles()).extracting(Role::getName).containsExactly(Roles.WAREHOUSE_STAFF);

        // Danh sách người dùng chỉ có tài khoản của Alpha; tài khoản PLATFORM coi như không tồn tại
        mockMvc.perform(get("/api/auth/users").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].username", not(hasItem("root_admin"))));
        mockMvc.perform(get("/api/auth/users").param("role", Roles.SUPER_ADMIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/auth/users/" + rootAdminId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/auth/users/" + rootAdminId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "x@wms.vn",
                                "roles", Set.of(Roles.ADMIN)))))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // Đăng ký và tác vụ định kỳ bỏ qua PLATFORM
    // ==========================================

    @Test
    @DisplayName("Đăng ký doanh nghiệp luôn nhận mã CTY, không bao giờ là PLATFORM; tác vụ định kỳ không quét PLATFORM")
    void registrationAndSchedulerSkipPlatform() throws Exception {
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("PLATFORM")
                .fullName("Người đăng ký")
                .username("platform")
                .email("platform@vidu.vn")
                .password(DEFAULT_PASSWORD)
                .build();
        String res = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String code = objectMapper.readTree(res).path("data").path("company").path("code").asText();
        assertThat(code).matches("CTY\\d{6}");
        assertThat(companyRepository.findByCode(Company.PLATFORM_CODE).orElseThrow().getId()).isEqualTo(platformId);

        // Nếu PLATFORM vì lý do nào đó có thuê bao, tác vụ định kỳ vẫn không quét nó
        insertSubscription(platformId, "FREE", "ACTIVE", null, null);
        Long platformSubscriptionId = subscriptionRepository.findByCompanyId(platformId).orElseThrow().getId();
        assertThat(subscriptionRepository.findAllIds()).doesNotContain(platformSubscriptionId).isNotEmpty();
        // Và nó cũng không được đếm trong tổng quan
        asSuperAdmin(get(PLATFORM + "/overview"))
                .andExpect(jsonPath("$.data.subscriptions.free").value(1));
    }

    // ==========================================
    // Hỗ trợ
    // ==========================================

    private List<MockHttpServletRequestBuilder> platformRequests() throws Exception {
        return List.of(
                get(PLATFORM + "/overview"),
                get(PLATFORM + "/companies"),
                get(PLATFORM + "/companies/" + alphaId),
                put(PLATFORM + "/companies/" + epsilonId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED"))),
                get(PLATFORM + "/payments"));
    }

    private ResultActions putStatus(long companyId, String status) throws Exception {
        return asSuperAdmin(put(PLATFORM + "/companies/" + companyId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", status))));
    }

    private void expectStatus(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.success").value(false));
    }

    private ResultActions asSuperAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()));
    }

    private String superAdminToken() {
        return tokenOf(rootAdminId, "root_admin", platformId, Company.PLATFORM_CODE, Roles.SUPER_ADMIN);
    }

    private String tokenOf(long userId, String username, long companyId, String companyCode, String role) {
        return jwtService.generateAccessToken(userId, username, companyId, companyCode, null, List.of(role), List.of());
    }

    private JsonNode loginData(String username) throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(res).path("data");
    }

    private Map<String, Object> createUserBody(String username, String role) {
        return Map.of("username", username, "email", username + "@vidu.vn", "password", DEFAULT_PASSWORD,
                "fullName", "Người dùng " + username, "roles", Set.of(role));
    }

    /** Thời điểm cách đây {@code months} tháng theo lịch giờ Việt Nam (luôn nằm trong đúng tháng đó). */
    private Instant monthsAgo(int months) {
        return ZonedDateTime.ofInstant(now, VN).minusMonths(months).toInstant();
    }

    private long insertCompany(String code, String name, String taxCode, String email, String status,
                               Instant createdAt) {
        jdbcTemplate.update("INSERT INTO companies (code, name, tax_code, email, status, activated_at, created_at, "
                        + "updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'system', 'system')",
                code, name, taxCode, email, status, "PENDING".equals(status) ? null : utc(createdAt),
                utc(createdAt), utc(createdAt));
        return jdbcTemplate.queryForObject("SELECT id FROM companies WHERE code = ?", Long.class, code);
    }

    private void insertSubscription(long companyId, String plan, String status, Instant trialEndsAt,
                                    Instant expiresAt) {
        jdbcTemplate.update("INSERT INTO subscriptions (company_id, plan_code, status, trial_ends_at, expires_at, "
                        + "created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, 'system', 'system')",
                companyId, plan, status, utc(trialEndsAt), utc(expiresAt), utc(now), utc(now));
    }

    private void insertPayment(long companyId, long orderCode, String cycle, long amount, String status,
                               Instant createdAt, Instant paidAt) {
        jdbcTemplate.update("INSERT INTO payments (company_id, order_code, plan_code, cycle, amount, status, paid_at, "
                        + "expires_at, created_at, updated_at, created_by, updated_by) "
                        + "VALUES (?, ?, 'PRO', ?, ?, ?, ?, ?, ?, ?, 'system', 'system')",
                companyId, orderCode, cycle, amount, status, utc(paidAt), utc(createdAt.plusSeconds(900)),
                utc(createdAt), utc(createdAt));
    }

    private long createUser(String username, long companyId, String role, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@wms.vn");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setFullName("Tài khoản " + username);
        user.setEnabled(enabled);
        user.setCompany(companyRepository.findById(companyId).orElseThrow());
        user.setRoles(new HashSet<>(Set.of(roleRepository.findByName(role).orElseThrow())));
        return userRepository.saveAndFlush(user).getId();
    }

    private static java.time.OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
