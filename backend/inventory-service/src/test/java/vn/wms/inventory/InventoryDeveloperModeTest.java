package vn.wms.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.tenant.TenantContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asRole;

/**
 * Chế độ phát triển (mặc định của cấu hình gốc, gateway tắt kiểm tra JWT): request không có danh tính đi qua kiểm tra
 * vai trò như trước WMS-20.3, còn các kiểm tra viết tay bắt buộc đăng nhập (kiểm kê, báo cáo) vẫn trả 401 như cũ.
 * Cùng cấu hình với InventoryServiceApplicationTest nên dùng lại context đã có.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventoryDeveloperModeTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestWithoutIdentityPassesRoleChecks() throws Exception {
        mockMvc.perform(get("/api/inventory/warehouses"))
                .andExpect(status().isOk());
        // API chỉ dành cho ADMIN vẫn chạy tới nghiệp vụ (kho không tồn tại: 404)
        mockMvc.perform(delete("/api/inventory/warehouses/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void handWrittenLoginRequirementIsUnchanged() throws Exception {
        mockMvc.perform(get("/api/inventory/stocktakes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithIdentityIsStillChecked() throws Exception {
        mockMvc.perform(delete("/api/inventory/warehouses/999999").with(asRole("WAREHOUSE_MANAGER", 1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
