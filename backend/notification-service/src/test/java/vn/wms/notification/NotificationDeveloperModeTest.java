package vn.wms.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.notification.service.EmailService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asRole;

/**
 * Chế độ phát triển (mặc định của cấu hình gốc, gateway tắt kiểm tra JWT): request không có danh tính đi qua kiểm tra
 * vai trò như trước WMS-20.3; request có danh tính vẫn bị kiểm tra. Cùng cấu hình với NotificationServiceApplicationTest
 * nên dùng lại context đã có.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationDeveloperModeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailService emailService;

    @Test
    void adminOnlyApiPassesWithoutIdentity() throws Exception {
        mockMvc.perform(get("/api/notifications/health-info"))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithIdentityIsStillChecked() throws Exception {
        mockMvc.perform(get("/api/notifications/health-info").with(asRole("SALES")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
