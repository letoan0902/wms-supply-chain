package vn.wms.auth.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.dto.auth.LoginRequest;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.dto.RenewMockRequest;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.auth.subscription.repository.SubscriptionRepository;

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
                .andExpect(jsonPath("$.data.admin.username").value("admin_anpha"))
                .andExpect(jsonPath("$.data.subscription.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        JsonNode regData = objectMapper.readTree(regResponseStr).path("data");
        long companyId = regData.path("company").path("id").asLong();

        // Kiểm tra Redis cache đã được đồng bộ
        Map<String, String> redisData = mockRedisHashMap.get("wms:subscription:" + companyId);
        assertThat(redisData).isNotNull();
        assertThat(redisData.get("plan")).isEqualTo("TRIAL");
        assertThat(redisData.get("status")).isEqualTo("ACTIVE");

        // Đăng nhập bằng tài khoản Admin mới tạo
        LoginRequest loginReq = new LoginRequest("admin_anpha", "Demopassword");
        String loginResponseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyId").value(companyId))
                .andExpect(jsonPath("$.data.subscription.plan").value("TRIAL"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponseStr).path("data").path("accessToken").asText();

        // Gọi GET /api/auth/subscription
        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.readOnly").value(false))
                .andExpect(jsonPath("$.data.daysRemaining").value(greaterThanOrEqualTo(29)))
                .andExpect(jsonPath("$.data.limits.maxUsers").value(50))
                .andExpect(jsonPath("$.data.limits.maxWarehouses").value(20))
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
                .andExpect(status().isConflict());

        // Kiểm tra số lượng Company trong DB không đổi (Transactional Rollback hoạt động)
        assertThat(companyRepository.count()).isEqualTo(afterFirstCount);
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

        mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

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

        // Kiểm tra lại subscription
        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("PRO"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.daysRemaining").value(greaterThanOrEqualTo(29)));
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

        mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

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

        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.readOnly").value(true));

        // 2. Hạ gói về FREE
        mockMvc.perform(post("/api/auth/subscription/downgrade")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.readOnly").value(false))
                .andExpect(jsonPath("$.data.limits.maxUsers").value(3))
                .andExpect(jsonPath("$.data.limits.maxWarehouses").value(1))
                .andExpect(jsonPath("$.data.limits.maxSkus").value(200));
    }
}
