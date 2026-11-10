package vn.wms.auth.payment.payos;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gọi PayOS thật: {@code POST {PAYOS_BASE_URL}/v2/payment-requests} với header {@code x-client-id}, {@code x-api-key}.
 * Thành công khi phản hồi có {@code code = "00"}; mọi lỗi quy về {@code SERVICE_UNAVAILABLE}, thông báo không chứa khóa.
 */
@Slf4j
public class HttpPayOsClient implements PayOsClient {

    static final int CONNECT_TIMEOUT_MS = 3_000;
    static final int READ_TIMEOUT_MS = 10_000;
    private static final String SUCCESS_CODE = "00";
    private static final String UNAVAILABLE_MESSAGE =
            "Không tạo được liên kết thanh toán PayOS, vui lòng thử lại sau";

    private final PayOsProperties properties;
    private final RestClient restClient;

    public HttpPayOsClient(PayOsProperties properties) {
        this(properties, RestClient.builder());
    }

    HttpPayOsClient(PayOsProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PaymentLink createPaymentLink(PaymentLinkRequest request) {
        if (!properties.hasCredentials()) {
            log.warn("Chưa cấu hình đủ PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY trong khi PAYOS_MOCK=false");
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Cổng thanh toán PayOS chưa được cấu hình, vui lòng liên hệ quản trị hệ thống");
        }

        String signature = PayOsSignature.signPaymentRequest(request.amount(), properties.cancelUrl(),
                request.description(), request.orderCode(), properties.returnUrl(), properties.checksumKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", request.orderCode());
        body.put("amount", request.amount());
        body.put("description", request.description());
        body.put("cancelUrl", properties.cancelUrl());
        body.put("returnUrl", properties.returnUrl());
        body.put("expiredAt", request.expiredAt().getEpochSecond());
        body.put("signature", signature);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v2/payment-requests")
                    .header("x-client-id", properties.clientId())
                    .header("x-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // Chỉ ghi mã HTTP, không ghi header hay nội dung yêu cầu (có khóa và chữ ký)
            log.warn("PayOS trả lỗi HTTP {} khi tạo liên kết cho đơn {}", e.getStatusCode().value(), request.orderCode());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        } catch (RestClientException e) {
            log.warn("Không kết nối được PayOS khi tạo liên kết cho đơn {}: {}", request.orderCode(),
                    e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }

        if (response == null || !SUCCESS_CODE.equals(response.path("code").asText())) {
            log.warn("PayOS từ chối tạo liên kết cho đơn {}: code={}, desc={}", request.orderCode(),
                    response == null ? null : response.path("code").asText(),
                    response == null ? null : response.path("desc").asText());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
        JsonNode data = response.path("data");
        String checkoutUrl = textOrNull(data, "checkoutUrl");
        if (checkoutUrl == null) {
            log.warn("PayOS trả thành công nhưng thiếu checkoutUrl cho đơn {}", request.orderCode());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
        return new PaymentLink(checkoutUrl, textOrNull(data, "qrCode"), textOrNull(data, "paymentLinkId"));
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
