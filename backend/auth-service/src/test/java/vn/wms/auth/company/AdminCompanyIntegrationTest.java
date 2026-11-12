package vn.wms.auth.company;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.dto.ApproveCompanyRequest;
import vn.wms.auth.company.dto.RejectCompanyRequest;
import vn.wms.auth.company.dto.UpdateCompanyStatusRequest;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.dto.auth.LoginRequest;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AdminCompanyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Quy trình hoàn chỉnh: Đăng ký (PENDING) -> Chặn login -> Admin duyệt (ACTIVE) -> Login thành công")
    void testCompanyRegistrationAndApprovalLifecycle() throws Exception {
        // 1. Khách hàng gửi form đăng ký từ Landing Page
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("Công ty TNHH Vận Tải Quốc Tế")
                .taxCode("0312998877")
                .phone("0909123456")
                .fullName("Lê Văn Quốc")
                .username("admin_quocte")
                .email("quocte@wms.vn")
                .password(DEFAULT_PASSWORD)
                .build();

        String regStr = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.company.status").value("PENDING"))
                .andExpect(jsonPath("$.data.admin.username").value("admin_quocte"))
                .andReturn().getResponse().getContentAsString();

        long companyId = objectMapper.readTree(regStr).path("data").path("company").path("id").asLong();

        // 2. Thử đăng nhập ngay trước khi được duyệt -> Bị chặn
        LoginRequest loginReq = new LoginRequest("admin_quocte", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));

        // 3. Admin toàn hệ thống đăng nhập và xem danh sách công ty chờ duyệt
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

        // 4. Admin xem chi tiết công ty
        mockMvc.perform(get("/api/auth/companies/" + companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Công ty TNHH Vận Tải Quốc Tế"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.adminUser.enabled").value(true));

        // 5. Admin phê duyệt công ty và cấp gói dùng thử 30 ngày
        ApproveCompanyRequest approveReq = new ApproveCompanyRequest("TRIAL", 30, null, "Xác minh qua điện thoại");
        mockMvc.perform(post("/api/auth/companies/" + companyId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.adminUser.enabled").value(true));

        // 6. Sau khi duyệt, khách hàng đăng nhập thành công
        String clientLoginStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.company.id").value(companyId))
                .andExpect(jsonPath("$.data.subscription.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(clientLoginStr).path("data").path("accessToken").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("Admin từ chối đơn đăng ký doanh nghiệp")
    void testRejectCompanyRegistration() throws Exception {
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("Công ty Ma Không Tồn Tại")
                .taxCode("0000000000")
                .phone("0900000000")
                .fullName("Nguyễn Ảo")
                .username("admin_ao")
                .email("ao@wms.vn")
                .password(DEFAULT_PASSWORD)
                .build();

        String regStr = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long companyId = objectMapper.readTree(regStr).path("data").path("company").path("id").asLong();

        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);
        RejectCompanyRequest rejectReq = new RejectCompanyRequest("Mã số thuế không có trên CSDL quốc gia");

        mockMvc.perform(post("/api/auth/companies/" + companyId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThat(companyRepository.findById(companyId).orElseThrow().getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Admin khóa và mở khóa doanh nghiệp")
    void testToggleCompanyStatus() throws Exception {
        String adminToken = loginAndGetToken("admin", DEFAULT_PASSWORD);

        // Khóa công ty mẫu B (id = 2)
        UpdateCompanyStatusRequest suspendReq = new UpdateCompanyStatusRequest(CompanyStatus.SUSPENDED);
        mockMvc.perform(patch("/api/auth/companies/2/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThat(companyRepository.findById(2L).orElseThrow().getStatus()).isEqualTo(CompanyStatus.SUSPENDED);

        // Mở khóa lại
        UpdateCompanyStatusRequest activeReq = new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE);
        mockMvc.perform(patch("/api/auth/companies/2/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(companyRepository.findById(2L).orElseThrow().getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }
}
