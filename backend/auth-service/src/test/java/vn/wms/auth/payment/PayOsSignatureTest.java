package vn.wms.auth.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.wms.auth.payment.payos.PayOsProperties;
import vn.wms.auth.payment.payos.PayOsSignature;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test đơn vị cho chữ ký PayOS (tiêu chí nghiệm thu 1 của WMS-23.5a). Giá trị mong đợi được tính độc lập ngay trong test
 * bằng javax.crypto trên chuỗi viết tay, không dùng lại mã của {@link PayOsSignature}.
 */
class PayOsSignatureTest {

    /** Khóa giả chỉ dùng trong test, không phải khóa PayOS thật. */
    private static final String FAKE_KEY = "test-checksum-key-khong-phai-khoa-that";

    /**
     * Khóa và chữ ký mẫu công khai trong tài liệu PayOS "Kiểm tra dữ liệu với signature" (payos.vn/docs),
     * không phải khóa của dự án. Dùng để khẳng định cách ghép chuỗi khớp với PayOS.
     */
    private static final String PAYOS_DOC_SAMPLE_KEY =
            "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
    private static final String PAYOS_DOC_SAMPLE_SIGNATURE =
            "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String hmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private ObjectNode sampleData(long orderCode, long amount) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("orderCode", orderCode);
        data.put("amount", amount);
        data.put("description", "VQRIO123");
        data.put("accountNumber", "12345678");
        data.put("reference", "TF230204212323");
        data.put("transactionDateTime", "2023-02-04 18:25:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "124c33293c43417ab7879e14c8d9eb18");
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    /** Chuỗi cần ký của sampleData viết tay theo thứ tự chữ cái của khóa. */
    private static String sampleDataString(long orderCode, long amount) {
        return "accountNumber=12345678&amount=" + amount + "&code=00&counterAccountBankId=&counterAccountBankName="
                + "&counterAccountName=&counterAccountNumber=&currency=VND&desc=Thành công&description=VQRIO123"
                + "&orderCode=" + orderCode + "&paymentLinkId=124c33293c43417ab7879e14c8d9eb18"
                + "&reference=TF230204212323&transactionDateTime=2023-02-04 18:25:00"
                + "&virtualAccountName=&virtualAccountNumber=";
    }

    @Test
    @DisplayName("Chữ ký yêu cầu tạo liên kết: chuỗi amount&cancelUrl&description&orderCode&returnUrl, không mã hóa URL")
    void paymentRequestSignatureMatchesIndependentHmac() throws Exception {
        String expectedData = "amount=499000&cancelUrl=https://wms.example.com/payment/result?cancel=1"
                + "&description=WMS PRO 1762761600123457&orderCode=1762761600123457"
                + "&returnUrl=https://wms.example.com/payment/result";
        String data = PayOsSignature.paymentRequestData(499000, "https://wms.example.com/payment/result?cancel=1",
                "WMS PRO 1762761600123457", 1762761600123457L, "https://wms.example.com/payment/result");
        assertThat(data).isEqualTo(expectedData);

        String signature = PayOsSignature.signPaymentRequest(499000, "https://wms.example.com/payment/result?cancel=1",
                "WMS PRO 1762761600123457", 1762761600123457L, "https://wms.example.com/payment/result", FAKE_KEY);
        assertThat(signature).isEqualTo(hmac(expectedData, FAKE_KEY)).matches("[0-9a-f]{64}");

        // Đổi số tiền hoặc dùng khóa khác thì chữ ký khác
        assertThat(PayOsSignature.signPaymentRequest(1, "https://wms.example.com/payment/result?cancel=1",
                "WMS PRO 1762761600123457", 1762761600123457L, "https://wms.example.com/payment/result", FAKE_KEY))
                .isNotEqualTo(signature);
        assertThat(PayOsSignature.signPaymentRequest(499000, "https://wms.example.com/payment/result?cancel=1",
                "WMS PRO 1762761600123457", 1762761600123457L, "https://wms.example.com/payment/result", "khoa-khac"))
                .isNotEqualTo(signature);
    }

    @Test
    @DisplayName("Chữ ký webhook khớp ví dụ trong tài liệu PayOS")
    void webhookSignatureMatchesPayOsDocumentationSample() {
        ObjectNode data = sampleData(123, 3000);
        assertThat(PayOsSignature.signWebhookData(data, PAYOS_DOC_SAMPLE_KEY)).isEqualTo(PAYOS_DOC_SAMPLE_SIGNATURE);
        assertThat(PayOsSignature.verifyWebhook(data, PAYOS_DOC_SAMPLE_SIGNATURE, PAYOS_DOC_SAMPLE_KEY)).isTrue();
    }

    @Test
    @DisplayName("Chữ ký webhook: khóa sắp xếp theo chữ cái, khớp HMAC tính độc lập")
    void webhookSignatureMatchesIndependentHmac() throws Exception {
        ObjectNode data = sampleData(1762761600123457L, 499000);
        assertThat(PayOsSignature.webhookData(data)).isEqualTo(sampleDataString(1762761600123457L, 499000));
        String expected = hmac(sampleDataString(1762761600123457L, 499000), FAKE_KEY);
        assertThat(PayOsSignature.signWebhookData(data, FAKE_KEY)).isEqualTo(expected);
        assertThat(PayOsSignature.verifyWebhook(data, expected, FAKE_KEY)).isTrue();
        // Chữ ký viết hoa vẫn hợp lệ (hex không phân biệt hoa thường)
        assertThat(PayOsSignature.verifyWebhook(data, expected.toUpperCase(), FAKE_KEY)).isTrue();
    }

    @Test
    @DisplayName("Webhook bị sửa số tiền, sửa mã đơn hoặc ký bằng khóa khác thì không hợp lệ")
    void tamperedWebhookIsRejected() throws Exception {
        String signature = hmac(sampleDataString(1762761600123457L, 499000), FAKE_KEY);

        assertThat(PayOsSignature.verifyWebhook(sampleData(1762761600123457L, 4990), signature, FAKE_KEY)).isFalse();
        assertThat(PayOsSignature.verifyWebhook(sampleData(1762761600123458L, 499000), signature, FAKE_KEY)).isFalse();
        assertThat(PayOsSignature.verifyWebhook(sampleData(1762761600123457L, 499000), signature, "khoa-sai")).isFalse();

        ObjectNode changedReference = sampleData(1762761600123457L, 499000);
        changedReference.put("reference", "FT000000000");
        assertThat(PayOsSignature.verifyWebhook(changedReference, signature, FAKE_KEY)).isFalse();

        // Thiếu chữ ký, thiếu khóa hoặc data không phải đối tượng
        assertThat(PayOsSignature.verifyWebhook(sampleData(1762761600123457L, 499000), "", FAKE_KEY)).isFalse();
        assertThat(PayOsSignature.verifyWebhook(sampleData(1762761600123457L, 499000), signature, "")).isFalse();
        assertThat(PayOsSignature.verifyWebhook(objectMapper.createArrayNode(), signature, FAKE_KEY)).isFalse();
    }

    @Test
    @DisplayName("Giá trị null thành chuỗi rỗng, mảng thành JSON với khóa từng phần tử đã sắp xếp")
    void nullAndNestedValues() throws Exception {
        JsonNode data = objectMapper.readTree(
                "{\"b\":null,\"a\":\"null\",\"c\":[{\"z\":1,\"y\":\"Hàng\"}],\"d\":true,\"e\":3000.0}");
        assertThat(PayOsSignature.webhookData(data))
                .isEqualTo("a=&b=&c=[{\"y\":\"Hàng\",\"z\":1}]&d=true&e=3000");
    }

    @Test
    @DisplayName("So sánh chuỗi thời gian hằng")
    void constantTimeEquals() {
        assertThat(PayOsSignature.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(PayOsSignature.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(PayOsSignature.constantTimeEquals("abc", "abcd")).isFalse();
        assertThat(PayOsSignature.constantTimeEquals(null, "abc")).isFalse();
    }

    @Test
    @DisplayName("Cấu hình PayOS: biến trống dùng mặc định và toString không lộ khóa")
    void propertiesDefaultsAndMasking() {
        PayOsProperties properties = new PayOsProperties("client-id-gia", "api-key-gia", FAKE_KEY, "", " ", "", true);
        assertThat(properties.returnUrl()).isEqualTo(PayOsProperties.DEFAULT_RETURN_URL);
        assertThat(properties.cancelUrl()).isEqualTo(PayOsProperties.DEFAULT_RETURN_URL);
        assertThat(properties.baseUrl()).isEqualTo("https://api-merchant.payos.vn");
        assertThat(properties.hasCredentials()).isTrue();
        assertThat(properties.toString()).doesNotContain("client-id-gia", "api-key-gia", FAKE_KEY);

        PayOsProperties empty = new PayOsProperties("", null, " ", null, null, null, false);
        assertThat(empty.hasCredentials()).isFalse();
        assertThat(empty.hasChecksumKey()).isFalse();
    }
}
