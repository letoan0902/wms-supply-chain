package vn.wms.auth.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import vn.wms.auth.BaseIntegrationTest;
import vn.wms.auth.payment.entity.Payment;
import vn.wms.auth.payment.entity.PaymentStatus;
import vn.wms.auth.payment.repository.PaymentRepository;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.auth.subscription.service.SubscriptionCacheWriter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp thanh toán PayOS ở chế độ giả lập (WMS-23.5a). Test đặt một checksum key giả để gửi webhook
 * có chữ ký tính độc lập bằng javax.crypto; không gọi PayOS thật.
 */
@TestPropertySource(properties = {
        "app.payos.mock=true",
        "app.payos.checksum-key=" + PaymentIntegrationTest.FAKE_CHECKSUM_KEY,
        "app.payos.return-url=http://localhost:5173/payment/result"
})
class PaymentIntegrationTest extends BaseIntegrationTest {

    /** Khóa giả chỉ dùng trong test, không phải khóa PayOS thật. */
    static final String FAKE_CHECKSUM_KEY = "test-checksum-key-khong-phai-khoa-that";

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /** Doanh nghiệp mới tự đăng ký (thuê bao TRIAL 30 ngày) và access token của ADMIN đầu tiên. */
    private record Tenant(long companyId, String token) {
    }

    private Tenant registerTenant() throws Exception {
        String suffix = System.nanoTime() + "_" + SEQ.incrementAndGet();
        RegisterCompanyRequest req = RegisterCompanyRequest.builder()
                .companyName("Công ty Thanh Toán " + suffix)
                .fullName("Quản trị " + suffix)
                .username("pay_" + suffix)
                .email("pay_" + suffix + "@wms.vn")
                .password(DEFAULT_PASSWORD)
                .build();
        String reg = mockMvc.perform(post("/api/auth/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = objectMapper.readTree(reg).path("data").path("company").path("id").asLong();
        activateCompanyByEmail("pay_" + suffix + "@wms.vn");
        return new Tenant(companyId, loginAndGetToken("pay_" + suffix, DEFAULT_PASSWORD));
    }

    private ResultActions createPayment(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/auth/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long createPaymentOk(String token, String cycle) throws Exception {
        String res = createPayment(token, "{\"plan\":\"PRO\",\"cycle\":\"" + cycle + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).path("data").path("orderCode").asLong();
    }

    private Subscription subscriptionOf(long companyId) {
        return subscriptionRepository.findByCompanyId(companyId).orElseThrow();
    }

    private Payment paymentOf(long orderCode) {
        return paymentRepository.findByOrderCode(orderCode).orElseThrow();
    }

    /** Body webhook theo mẫu của PayOS; chữ ký tính độc lập: sắp xếp khóa của data, nối key=value, HMAC-SHA256. */
    private String webhookBody(long orderCode, long amount, String key) throws Exception {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("orderCode", orderCode);
        data.put("amount", amount);
        data.put("description", "WMS PRO " + orderCode);
        data.put("accountNumber", "12345678");
        data.put("reference", "FT" + orderCode);
        data.put("transactionDateTime", "2026-11-10 10:00:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "link-" + orderCode);
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");

        Map<String, String> sorted = new TreeMap<>();
        data.fields().forEachRemaining(e -> sorted.put(e.getKey(), e.getValue().asText()));
        List<String> parts = new ArrayList<>();
        sorted.forEach((k, v) -> parts.add(k + "=" + v));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(
                mac.doFinal(String.join("&", parts).getBytes(StandardCharsets.UTF_8)));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "00");
        body.put("desc", "success");
        body.put("success", true);
        body.set("data", data);
        body.put("signature", signature);
        return objectMapper.writeValueAsString(body);
    }

    private ResultActions sendWebhook(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/payments/payos/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("Tạo thanh toán ở chế độ giả lập: PENDING, có liên kết tới trang giả lập, số tiền lấy từ bảng gói")
    void createPaymentInMockMode() throws Exception {
        Tenant tenant = registerTenant();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Client gửi kèm amount = 1 nhưng bị bỏ qua: số tiền luôn là giá gói PRO theo tháng
        String res = createPayment(tenant.token(), "{\"plan\":\"PRO\",\"cycle\":\"MONTHLY\",\"amount\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value(499000))
                .andExpect(jsonPath("$.data.plan").value("PRO"))
                .andExpect(jsonPath("$.data.cycle").value("MONTHLY"))
                .andExpect(jsonPath("$.data.checkoutUrl", startsWith("http://localhost:5173/payment/mock?orderCode=")))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(res).path("data");
        long orderCode = data.path("orderCode").asLong();
        assertThat(orderCode).isPositive().isLessThanOrEqualTo(9_007_199_254_740_991L);
        assertThat(data.path("checkoutUrl").asText()).endsWith("orderCode=" + orderCode);
        assertThat(Instant.parse(data.path("expiresAt").asText()))
                .isBetween(before.plus(15, ChronoUnit.MINUTES), Instant.now().plus(15, ChronoUnit.MINUTES));

        Payment payment = paymentOf(orderCode);
        assertThat(payment.getCompanyId()).isEqualTo(tenant.companyId());
        assertThat(payment.getAmount()).isEqualTo(499000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCreatedByUser()).isNotNull();

        // Chu kỳ năm lấy giá năm
        createPayment(tenant.token(), "{\"plan\":\"PRO\",\"cycle\":\"YEARLY\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(4790000));

        // Chỉ nhận gói PRO; thiếu chu kỳ là lỗi dữ liệu
        createPayment(tenant.token(), "{\"plan\":\"TRIAL\",\"cycle\":\"MONTHLY\"}")
                .andExpect(status().isBadRequest());
        createPayment(tenant.token(), "{\"plan\":\"PRO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Lịch sử giao dịch: mới nhất trước
        mockMvc.perform(get("/api/auth/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].cycle").value("YEARLY"))
                .andExpect(jsonPath("$.data.content[1].orderCode").value(orderCode));
    }

    @Test
    @DisplayName("mock-confirm: giao dịch PAID, thuê bao PRO, ngày hết hạn tăng đúng 30 rồi 365 ngày theo công thức")
    void mockConfirmActivatesPlan() throws Exception {
        Tenant tenant = registerTenant();
        Instant trialEndsAt = subscriptionOf(tenant.companyId()).getTrialEndsAt();

        long monthly = createPaymentOk(tenant.token(), "MONTHLY");
        mockMvc.perform(post("/api/auth/payments/" + monthly + "/mock-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());

        Subscription afterMonthly = subscriptionOf(tenant.companyId());
        assertThat(afterMonthly.getPlan().getCode()).isEqualTo("PRO");
        // Công thức mục 2.6: max(hiện tại, mốc dùng thử còn hiệu lực) + 30 ngày
        assertThat(afterMonthly.getExpiresAt()).isEqualTo(trialEndsAt.plus(30, ChronoUnit.DAYS));
        assertThat(mockRedisHashMap.get(SubscriptionCacheWriter.keyOf(tenant.companyId())))
                .containsEntry("plan", "PRO")
                .containsEntry("status", "ACTIVE")
                .containsEntry("expiresAt", afterMonthly.getExpiresAt().toString());

        long yearly = createPaymentOk(tenant.token(), "YEARLY");
        mockMvc.perform(post("/api/auth/payments/" + yearly + "/mock-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk());
        Instant afterYearly = subscriptionOf(tenant.companyId()).getExpiresAt();
        assertThat(afterYearly).isEqualTo(afterMonthly.getExpiresAt().plus(365, ChronoUnit.DAYS));

        // Xác nhận lần hai không cộng thêm ngày
        mockMvc.perform(post("/api/auth/payments/" + yearly + "/mock-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
        assertThat(subscriptionOf(tenant.companyId()).getExpiresAt()).isEqualTo(afterYearly);

        mockMvc.perform(get("/api/auth/payments/" + yearly)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amount").value(4790000));
    }

    @Test
    @DisplayName("Webhook có chữ ký đúng: PAID và kích hoạt gói; gửi lặp trả 200 và không cộng thêm ngày")
    void validWebhookThenRepeated() throws Exception {
        Tenant tenant = registerTenant();
        Instant trialEndsAt = subscriptionOf(tenant.companyId()).getTrialEndsAt();
        long orderCode = createPaymentOk(tenant.token(), "MONTHLY");
        String body = webhookBody(orderCode, 499000, FAKE_CHECKSUM_KEY);

        sendWebhook(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Payment payment = paymentOf(orderCode);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getReference()).isEqualTo("FT" + orderCode);
        assertThat(payment.getPaidAt()).isNotNull();
        Instant expiry = subscriptionOf(tenant.companyId()).getExpiresAt();
        assertThat(subscriptionOf(tenant.companyId()).getPlan().getCode()).isEqualTo("PRO");
        assertThat(expiry).isEqualTo(trialEndsAt.plus(30, ChronoUnit.DAYS));

        // PayOS gửi lại cùng webhook
        sendWebhook(body).andExpect(status().isOk());
        assertThat(subscriptionOf(tenant.companyId()).getExpiresAt()).isEqualTo(expiry);
        assertThat(paymentOf(orderCode).getPaidAt()).isEqualTo(payment.getPaidAt());
    }

    @Test
    @DisplayName("Webhook sai chữ ký hoặc sai số tiền: 400 PAYMENT_INVALID, giao dịch và thuê bao không đổi")
    void invalidWebhookChangesNothing() throws Exception {
        Tenant tenant = registerTenant();
        Subscription before = subscriptionOf(tenant.companyId());
        long orderCode = createPaymentOk(tenant.token(), "MONTHLY");

        // Ký bằng khóa khác
        sendWebhook(webhookBody(orderCode, 499000, "khoa-cua-ke-gia-mao"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));

        // Chữ ký đúng nhưng số tiền khác số tiền đã lưu
        sendWebhook(webhookBody(orderCode, 1000, FAKE_CHECKSUM_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));

        // Sửa số tiền trong body sau khi ký
        String tampered = webhookBody(orderCode, 499000, FAKE_CHECKSUM_KEY)
                .replace("\"amount\":499000", "\"amount\":499");
        sendWebhook(tampered)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));

        // Thiếu chữ ký
        sendWebhook("{\"code\":\"00\",\"data\":{\"orderCode\":" + orderCode + ",\"amount\":499000}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));

        assertThat(paymentOf(orderCode).getStatus()).isEqualTo(PaymentStatus.PENDING);
        Subscription after = subscriptionOf(tenant.companyId());
        assertThat(after.getPlan().getCode()).isEqualTo(before.getPlan().getCode());
        assertThat(after.getTrialEndsAt()).isEqualTo(before.getTrialEndsAt());
        assertThat(after.getExpiresAt()).isEqualTo(before.getExpiresAt());
    }

    @Test
    @DisplayName("Webhook chữ ký đúng nhưng mã đơn không tồn tại: 400, trừ đơn mẫu 123 của PayOS (200)")
    void unknownOrderCode() throws Exception {
        sendWebhook(webhookBody(987_654_321L, 3000, FAKE_CHECKSUM_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));

        sendWebhook(webhookBody(123L, 3000, FAKE_CHECKSUM_KEY))
                .andExpect(status().isOk());

        // Đơn mẫu nhưng sai chữ ký vẫn bị từ chối
        sendWebhook(webhookBody(123L, 3000, "khoa-sai"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Giao dịch quá hạn được chuyển EXPIRED khi đọc; webhook hợp lệ tới sau vẫn được coi là đã thanh toán")
    void expiredPaymentThenPaidWebhook() throws Exception {
        Tenant tenant = registerTenant();
        long orderCode = createPaymentOk(tenant.token(), "MONTHLY");
        jdbcTemplate.update("UPDATE payments SET expires_at = ? WHERE order_code = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), orderCode);

        mockMvc.perform(get("/api/auth/payments/" + orderCode)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));

        // Đã hết hạn thì không hủy được
        mockMvc.perform(post("/api/auth/payments/" + orderCode + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isConflict());

        sendWebhook(webhookBody(orderCode, 499000, FAKE_CHECKSUM_KEY)).andExpect(status().isOk());
        assertThat(paymentOf(orderCode).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(subscriptionOf(tenant.companyId()).getPlan().getCode()).isEqualTo("PRO");
    }

    @Test
    @DisplayName("Hủy giao dịch: PENDING thành CANCELLED, hủy lần hai hoặc hủy đơn đã PAID trả 409")
    void cancelFlow() throws Exception {
        Tenant tenant = registerTenant();
        long orderCode = createPaymentOk(tenant.token(), "MONTHLY");

        mockMvc.perform(post("/api/auth/payments/" + orderCode + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(paymentOf(orderCode).getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        mockMvc.perform(post("/api/auth/payments/" + orderCode + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        long paid = createPaymentOk(tenant.token(), "MONTHLY");
        mockMvc.perform(post("/api/auth/payments/" + paid + "/mock-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/payments/" + paid + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isConflict());
        assertThat(paymentOf(paid).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("ADMIN doanh nghiệp khác không xem, không hủy, không xác nhận được giao dịch (404)")
    void otherCompanyCannotAccess() throws Exception {
        Tenant tenant = registerTenant();
        long orderCode = createPaymentOk(tenant.token(), "MONTHLY");
        String otherAdmin = loginAndGetToken("admin_b", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/payments/" + orderCode)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/payments/" + orderCode + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/payments/" + orderCode + "/mock-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.orderCode == " + orderCode + ")]").isEmpty());

        assertThat(paymentOf(orderCode).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Tài khoản không phải ADMIN nhận 403, chưa đăng nhập nhận 401")
    void nonAdminForbidden() throws Exception {
        String staffToken = loginAndGetToken("staff", DEFAULT_PASSWORD);
        createPayment(staffToken, "{\"plan\":\"PRO\",\"cycle\":\"MONTHLY\"}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\",\"cycle\":\"MONTHLY\"}"))
                .andExpect(status().isUnauthorized());
    }
}
