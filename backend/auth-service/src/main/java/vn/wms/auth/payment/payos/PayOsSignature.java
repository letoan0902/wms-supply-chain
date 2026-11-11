package vn.wms.auth.payment.payos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tạo và kiểm tra chữ ký PayOS (HMAC-SHA256 bằng checksum key, kết quả hex chữ thường).
 * Các hàm thuần, không giữ trạng thái, không ghi log.
 *
 * <p>Theo tài liệu PayOS "Kiểm tra dữ liệu với signature" (payos.vn/docs):
 * <ul>
 *     <li>Yêu cầu tạo liên kết thanh toán: ký chuỗi
 *     {@code amount=..&cancelUrl=..&description=..&orderCode=..&returnUrl=..} (khóa theo thứ tự chữ cái,
 *     giá trị giữ nguyên, không mã hóa URL).</li>
 *     <li>Webhook: lấy đối tượng {@code data}, sắp xếp khóa theo chữ cái, nối {@code key=value} bằng {@code &}.
 *     Giá trị null (và chuỗi "null", "undefined" như mã mẫu của PayOS) thành chuỗi rỗng; mảng được chuyển thành JSON
 *     sau khi sắp xếp khóa của từng phần tử; đối tượng lồng nhau chuyển thành JSON.</li>
 * </ul>
 */
public final class PayOsSignature {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PayOsSignature() {
    }

    /** Chuỗi cần ký của yêu cầu tạo liên kết thanh toán. */
    public static String paymentRequestData(long amount, String cancelUrl, String description, long orderCode,
                                            String returnUrl) {
        return "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
    }

    /** Chữ ký của yêu cầu tạo liên kết thanh toán ({@code POST /v2/payment-requests}). */
    public static String signPaymentRequest(long amount, String cancelUrl, String description, long orderCode,
                                            String returnUrl, String checksumKey) {
        return hmacSha256Hex(paymentRequestData(amount, cancelUrl, description, orderCode, returnUrl), checksumKey);
    }

    /** Chuỗi cần ký của đối tượng {@code data} trong webhook: khóa sắp xếp theo chữ cái, nối {@code key=value&...}. */
    public static String webhookData(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("data của webhook phải là một đối tượng JSON");
        }
        Map<String, JsonNode> sorted = new TreeMap<>();
        data.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
        List<String> parts = new ArrayList<>(sorted.size());
        sorted.forEach((key, value) -> parts.add(key + "=" + valueToString(value)));
        return String.join("&", parts);
    }

    /** Chữ ký PayOS tính cho đối tượng {@code data} của webhook. */
    public static String signWebhookData(JsonNode data, String checksumKey) {
        return hmacSha256Hex(webhookData(data), checksumKey);
    }

    /**
     * Kiểm tra chữ ký webhook: tính lại chữ ký của {@code data} rồi so sánh với {@code signature} trong body
     * bằng phép so sánh thời gian hằng (không lộ vị trí ký tự sai qua thời gian phản hồi).
     * Thiếu dữ liệu, thiếu khóa hoặc chữ ký rỗng đều trả {@code false}.
     */
    public static boolean verifyWebhook(JsonNode data, String signature, String checksumKey) {
        if (data == null || !data.isObject() || signature == null || signature.isBlank()
                || checksumKey == null || checksumKey.isBlank()) {
            return false;
        }
        String expected = signWebhookData(data, checksumKey);
        return constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    /** So sánh hai chuỗi trong thời gian không phụ thuộc vào vị trí khác nhau đầu tiên. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** HMAC-SHA256 của chuỗi UTF-8, trả hex chữ thường. */
    public static String hmacSha256Hex(String data, String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Chưa cấu hình checksum key của PayOS");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                out[i * 2] = HEX[(digest[i] >> 4) & 0x0F];
                out[i * 2 + 1] = HEX[digest[i] & 0x0F];
            }
            return new String(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không tính được HMAC-SHA256", e);
        }
    }

    /** Giá trị của một trường trong chuỗi cần ký, theo cách mã mẫu của PayOS xử lý. */
    private static String valueToString(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isTextual()) {
            String text = value.asText();
            return "null".equals(text) || "undefined".equals(text) ? "" : text;
        }
        if (value.isIntegralNumber()) {
            return value.bigIntegerValue().toString();
        }
        if (value.isNumber()) {
            // Số thực nguyên (3000.0) in như JavaScript: 3000
            return value.decimalValue().stripTrailingZeros().toPlainString();
        }
        if (value.isBoolean()) {
            return String.valueOf(value.booleanValue());
        }
        if (value.isArray()) {
            return sortArrayElements((ArrayNode) value).toString();
        }
        return value.toString();
    }

    /** Mảng: sắp xếp khóa của từng phần tử là đối tượng (giống mã mẫu Node.js và PHP của PayOS), giữ thứ tự phần tử. */
    private static ArrayNode sortArrayElements(ArrayNode array) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode element : array) {
            if (element.isObject()) {
                ObjectNode sortedElement = JsonNodeFactory.instance.objectNode();
                Map<String, JsonNode> sorted = new TreeMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = element.fields();
                fields.forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
                sorted.forEach(sortedElement::set);
                result.add(sortedElement);
            } else {
                result.add(element);
            }
        }
        return result;
    }
}
