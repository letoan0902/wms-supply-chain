package vn.wms.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asRole;

/**
 * Chế độ phát triển (mặc định của cấu hình gốc, gateway tắt kiểm tra JWT): request không có danh tính đi qua kiểm tra
 * vai trò như trước WMS-20.3; request có danh tính vẫn bị kiểm tra. Cùng cấu hình với các test tích hợp khác nên dùng
 * lại context đã có.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestExecutionListeners(listeners = DefaultCompanyTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class ProductDeveloperModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutIdentityPassesRoleChecks() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
        // API chỉ dành cho ADMIN và WAREHOUSE_MANAGER vẫn chạy tới nghiệp vụ (sản phẩm không tồn tại: 404)
        mockMvc.perform(delete("/api/products/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithIdentityIsStillChecked() throws Exception {
        mockMvc.perform(delete("/api/products/999999").with(asRole("SALES")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
