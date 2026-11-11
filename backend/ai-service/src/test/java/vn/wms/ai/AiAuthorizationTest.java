package vn.wms.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asIntegration;
import static vn.wms.common.test.GatewayHeaders.asRole;
import static vn.wms.common.test.GatewayHeaders.companyOnly;

/**
 * ai-service ở chế độ máy chủ ({@code app.security.allow-anonymous-without-gateway=false}, WMS-20.3): mọi vai trò tài
 * khoản gọi được trợ lý (vai trò theo loại dữ liệu của trích xuất kiểm tra trong service); request chỉ có doanh nghiệp
 * mà không có người dùng nhận 401; API key (INTEGRATION) không gọi được ai-service.
 */
@SpringBootTest(properties = "app.security.allow-anonymous-without-gateway=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutUserIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/ai/health").with(companyOnly(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void everyAccountRoleReachesTheService() throws Exception {
        for (String role : vn.wms.common.constant.Roles.ALL) {
            mockMvc.perform(get("/api/ai/health").with(asRole(role, 1L)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void integrationRoleCannotCallAi() throws Exception {
        mockMvc.perform(get("/api/ai/health").with(asIntegration(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
