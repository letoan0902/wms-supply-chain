package vn.wms.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.security.sample.SecuredSampleApplication;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asIntegration;
import static vn.wms.common.test.GatewayHeaders.asRole;
import static vn.wms.common.test.GatewayHeaders.companyOnly;

/**
 * Chế độ máy chủ ({@code app.security.allow-anonymous-without-gateway=false}): request thiếu danh tính nhận 401,
 * vai trò không đủ nhận 403, cả hai theo khung ApiResponse; vai trò INTEGRATION chỉ đọc; đường dẫn nội bộ cần
 * doanh nghiệp nhưng không cần người dùng.
 */
@SpringBootTest(classes = SecuredSampleApplication.class, properties = {
        "app.security.allow-anonymous-without-gateway=false",
        "spring.datasource.url=jdbc:h2:mem:security-strict;DB_CLOSE_DELAY=-1",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
class GatewaySecurityStrictModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutIdentityIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/products/sample"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/products/sample"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void allowedRolePasses() throws Exception {
        mockMvc.perform(get("/api/products/sample").with(asRole("SALES")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("sales"));
        mockMvc.perform(post("/api/products/sample").with(asRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void otherRoleIsForbiddenWithApiResponseBody() throws Exception {
        mockMvc.perform(get("/api/products/sample").with(asRole("WAREHOUSE_STAFF", 1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này"));
        mockMvc.perform(post("/api/products/sample").with(asRole("SALES")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void integrationReadsButCannotWrite() throws Exception {
        mockMvc.perform(get("/api/products/sample").with(asIntegration(1L)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/products/sample").with(asIntegration(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalPathNeedsCompanyButNoUser() throws Exception {
        mockMvc.perform(get("/internal/sample").with(companyOnly(3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(GatewayIdentityFilter.INTERNAL_SERVICE_PRINCIPAL));
        mockMvc.perform(get("/internal/sample"))
                .andExpect(status().isUnauthorized());
        // Có người dùng chuyển tiếp thì lời gọi nội bộ mang đúng người đó
        mockMvc.perform(get("/internal/sample").with(asRole("ADMIN")))
                .andExpect(jsonPath("$.data").value("admin"));
    }

    @Test
    void infrastructurePathsStayOpen() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(not(401)));
    }

    @Test
    void unexpectedErrorDoesNotLeakDetails() throws Exception {
        mockMvc.perform(get("/api/products/sample/boom").with(asRole("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("chi tiết nội bộ"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
