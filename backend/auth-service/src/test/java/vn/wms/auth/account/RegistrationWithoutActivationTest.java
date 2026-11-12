package vn.wms.auth.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Máy phát triển không có mail tắt {@code app.registration.require-activation} (biến
 * {@code REGISTRATION_REQUIRE_ACTIVATION=false}): doanh nghiệp hoạt động ngay, không phát liên kết kích hoạt.
 */
@TestPropertySource(properties = "app.registration.require-activation=false")
class RegistrationWithoutActivationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Tắt yêu cầu kích hoạt: doanh nghiệp ACTIVE ngay, không có mã, không phát sự kiện, đăng nhập được")
    void registrationActivatesImmediatelyWhenSwitchIsOff() throws Exception {
        String body = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterCompanyRequest.builder()
                                .companyName("Công ty Không Mail")
                                .fullName("Người Thử Không Mail")
                                .username("admin_khongmail")
                                .email("khongmail@wms.vn")
                                .password(DEFAULT_PASSWORD)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.activationRequired").value(false))
                .andExpect(jsonPath("$.data.company.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(body).path("data").path("company").path("id").asLong();

        Company company = companyRepository.findById(companyId).orElseThrow();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getActivatedAt()).isNotNull();
        assertThat(publishedEvents).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_tokens WHERE company_id = ?",
                Long.class, companyId)).isZero();

        loginAndGetToken("admin_khongmail", DEFAULT_PASSWORD);
    }
}
