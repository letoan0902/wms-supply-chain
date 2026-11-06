package vn.wms.auth.company;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.dto.CreateCompanyRequest;
import vn.wms.auth.company.dto.UpdateCompanyRequest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.dto.auth.LoginRequest;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.common.constant.Roles;

import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CompanyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    private String systemAdminToken;
    private String companyAAdminToken;
    private String companyBAdminToken;

    @BeforeEach
    void setUpCompaniesAndTokens() throws Exception {
        // 1. Tạo 2 công ty mẫu nếu chưa có
        Company compA = companyRepository.findByCode("LOGIX").orElseGet(() -> {
            Company c = new Company();
            c.setCode("LOGIX");
            c.setName("Công ty LogiX");
            c.setTaxCode("0101234567");
            c.setStatus("ACTIVE");
            return companyRepository.save(c);
        });

        Company compB = companyRepository.findByCode("FASTMOVE").orElseGet(() -> {
            Company c = new Company();
            c.setCode("FASTMOVE");
            c.setName("Công ty FastMove");
            c.setTaxCode("0309876543");
            c.setStatus("ACTIVE");
            return companyRepository.save(c);
        });

        Role adminRole = roleRepository.findByName(Roles.ADMIN).orElseThrow();

        // 2. Tạo User Admin cho Công ty A
        if (userRepository.findByUsername("admin_logix").isEmpty()) {
            User adminA = new User();
            adminA.setUsername("admin_logix");
            adminA.setEmail("admin@logix.vn");
            adminA.setPasswordHash(passwordEncoder.encode("Demopassword"));
            adminA.setEnabled(true);
            adminA.setCompanyId(compA.getId());
            adminA.setRoles(Set.of(adminRole));
            userRepository.save(adminA);
        }

        // 3. Tạo User Admin cho Công ty B
        if (userRepository.findByUsername("admin_fastmove").isEmpty()) {
            User adminB = new User();
            adminB.setUsername("admin_fastmove");
            adminB.setEmail("admin@fastmove.vn");
            adminB.setPasswordHash(passwordEncoder.encode("Demopassword"));
            adminB.setEnabled(true);
            adminB.setCompanyId(compB.getId());
            adminB.setRoles(Set.of(adminRole));
            userRepository.save(adminB);
        }

        // 4. Lấy tokens
        systemAdminToken = loginAndGetToken("admin", "Demopassword");
        companyAAdminToken = loginAndGetToken("admin_logix", "Demopassword");
        companyBAdminToken = loginAndGetToken("admin_fastmove", "Demopassword");
    }

    @Test
    void loginCompanyAUserReturnsJwtWithCompanyId1AndCompanyInfo() throws Exception {
        LoginRequest request = new LoginRequest("admin_logix", "Demopassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin_logix"))
                .andExpect(jsonPath("$.data.companyId").value(1))
                .andExpect(jsonPath("$.data.companyCode").value("LOGIX"))
                .andExpect(jsonPath("$.data.companyName").value(containsStringIgnoringCase("LogiX")));
    }

    @Test
    void loginCompanyBUserReturnsJwtWithCompanyId2AndCompanyInfo() throws Exception {
        LoginRequest request = new LoginRequest("admin_fastmove", "Demopassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin_fastmove"))
                .andExpect(jsonPath("$.data.companyId").value(2))
                .andExpect(jsonPath("$.data.companyCode").value("FASTMOVE"))
                .andExpect(jsonPath("$.data.companyName").value(containsStringIgnoringCase("FastMove")));
    }

    @Test
    void loginSystemAdminReturnsNullCompanyId() throws Exception {
        LoginRequest request = new LoginRequest("admin", "Demopassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.companyId").isEmpty());
    }

    @Test
    void systemAdminCanListAllCompanies() throws Exception {
        mockMvc.perform(get("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void companyAdminCanAccessTheirOwnCompanyProfile() throws Exception {
        mockMvc.perform(get("/api/companies/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyAAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("LOGIX"));
    }

    @Test
    void companyAdminCannotAccessOtherCompanyDataThrowsForbidden() throws Exception {
        // Admin Công ty B cố tình truy cập thông tin Công ty A (id = 1) -> 403 Forbidden
        mockMvc.perform(get("/api/companies/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyBAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("quyền")));
    }

    @Test
    void systemAdminCanCreateCompany() throws Exception {
        CreateCompanyRequest request = new CreateCompanyRequest(
                "TESTCOMP", "Công ty Thử Nghiệm", "9988776655", "test@comp.vn", "0900000000", "Hà Nội"
        );

        mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("TESTCOMP"));
    }

    @Test
    void companyAdminCannotCreateCompanyThrowsForbidden() throws Exception {
        CreateCompanyRequest request = new CreateCompanyRequest(
                "TESTCOMP2", "Công ty B trái phép", "9988776656", "test2@comp.vn", "0900000001", "HCM"
        );

        mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyAAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemAdminCanToggleCompanyStatus() throws Exception {
        mockMvc.perform(patch("/api/companies/2/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAdminToken)
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        // Khôi phục lại ACTIVE để các test khác không bị ảnh hưởng
        mockMvc.perform(patch("/api/companies/2/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAdminToken)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
