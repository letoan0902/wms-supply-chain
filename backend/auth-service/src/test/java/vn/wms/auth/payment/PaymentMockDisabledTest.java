package vn.wms.auth.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import vn.wms.auth.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PAYOS_MOCK=false} và chưa có khóa PayOS: mock-confirm trả 404, tạo thanh toán trả 503 mà không gọi mạng,
 * webhook bị từ chối vì không có checksum key để xác minh.
 */
@TestPropertySource(properties = {
        "app.payos.mock=false",
        "app.payos.client-id=",
        "app.payos.api-key=",
        "app.payos.checksum-key="
})
class PaymentMockDisabledTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Tắt giả lập: mock-confirm 404, thiếu khóa thì tạo thanh toán 503, webhook không xác minh được thì 400")
    void mockDisabledWithoutKeys() throws Exception {
        String token = loginAndGetToken("admin", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/auth/payments/1762761600123457/mock-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\",\"cycle\":\"MONTHLY\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

        mockMvc.perform(post("/api/auth/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"00\",\"data\":{\"orderCode\":123,\"amount\":3000},\"signature\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
    }
}
