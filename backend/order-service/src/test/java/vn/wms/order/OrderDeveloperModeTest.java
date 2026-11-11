package vn.wms.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asRole;

/**
 * Chế độ phát triển (mặc định của cấu hình gốc, gateway tắt kiểm tra JWT): request không có danh tính đi qua kiểm tra
 * vai trò như trước WMS-20.3; request có danh tính vẫn bị kiểm tra.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderDeveloperModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutIdentityPassesRoleChecks() throws Exception {
        mockMvc.perform(get("/api/orders/purchase-bills"))
                .andExpect(status().isOk());
        // API ghi chỉ dành cho ADMIN và SALES vẫn chạy tới nghiệp vụ (đơn không tồn tại: 404)
        mockMvc.perform(post("/api/orders/sales/999999/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithIdentityIsStillChecked() throws Exception {
        mockMvc.perform(get("/api/orders/purchase-bills").with(asRole("SALES")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
