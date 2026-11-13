package vn.wms.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.security.sample.SecuredSampleApplication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asRole;

/**
 * Chế độ phát triển (mặc định, gateway tắt kiểm tra JWT): request không có danh tính đi qua như nhà phát triển có
 * đủ vai trò; request có danh tính vẫn bị kiểm tra vai trò như trên máy chủ.
 */
@SpringBootTest(classes = SecuredSampleApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:security-dev;DB_CLOSE_DELAY=-1",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
class GatewaySecurityDeveloperModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutIdentityPassesThrough() throws Exception {
        mockMvc.perform(get("/api/products/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(GatewayIdentityFilter.DEVELOPER_PRINCIPAL));
        mockMvc.perform(post("/api/products/sample"))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithIdentityIsStillChecked() throws Exception {
        mockMvc.perform(post("/api/products/sample").with(asRole("SALES")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
