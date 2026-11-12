package vn.wms.ai.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.wms.ai.dto.BulkImportResult;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.List;
import java.util.Map;

/**
 * Gọi API nhập hàng loạt của WMS-22.4b ({@code POST /api/products/bulk} trên product-service,
 * {@code POST /api/orders/suppliers/bulk} và {@code /api/orders/customers/bulk} trên order-service) với body
 * {@code { "items": [...] }}, qua client cân bằng tải của {@code ServiceClientConfig} (chuyển tiếp danh tính người
 * gọi).
 *
 * <p>Lỗi nghiệp vụ của service phía sau (400 dữ liệu sai, 403 không đủ quyền, hết hạn thuê bao, vượt giới hạn gói)
 * được trả lại nguyên mã và thông báo để giao diện xử lý như khi gọi trực tiếp. Service phía sau chưa chạy, chưa có
 * API (404) hoặc lỗi 5xx thì trả {@code 503 SERVICE_UNAVAILABLE}.
 */
@Component
public class BulkImportClient {

    private static final Logger log = LoggerFactory.getLogger(BulkImportClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BulkImportClient(RestClient.Builder loadBalancedRestClientBuilder, ObjectMapper objectMapper) {
        this.restClient = loadBalancedRestClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public BulkImportResult importItems(ExtractionDataType type, List<Map<String, Object>> items) {
        String url = "http://" + type.serviceId() + type.bulkPath();
        ApiResponse<BulkImportResult> response;
        try {
            response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("items", items))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<BulkImportResult>>() {
                    });
        } catch (RestClientResponseException ex) {
            throw translate(type, ex);
        } catch (RestClientException | IllegalStateException ex) {
            // IllegalStateException: Spring Cloud LoadBalancer không tìm thấy instance nào của service.
            log.warn("Không gọi được {} để nhập {}: {}", type.serviceId(), type.name(), ex.getClass().getSimpleName());
            throw unavailable(type);
        }
        if (response == null || response.data() == null) {
            log.warn("{} trả kết quả nhập hàng loạt rỗng", type.serviceId());
            throw unavailable(type);
        }
        return response.data();
    }

    private ApiException translate(ExtractionDataType type, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status >= 500 || status == 404 || status == 405) {
            log.warn("{} trả HTTP {} khi nhập {}", type.serviceId(), status, type.name());
            return unavailable(type);
        }
        ErrorCode code = ErrorCode.fromHttpStatus(status);
        String message = code.getDefaultMessage();
        Object data = null;
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            if (body != null && body.isObject()) {
                code = parseCode(body.path("code").asText(null), code);
                message = body.path("message").asText(message);
                JsonNode dataNode = body.path("data");
                data = dataNode.isMissingNode() || dataNode.isNull() ? null : dataNode;
            }
        } catch (Exception parseError) {
            // Body không phải ApiResponse: giữ mã suy ra từ HTTP status.
        }
        return ApiException.withData(code, message, data);
    }

    private static ErrorCode parseCode(String value, ErrorCode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return ErrorCode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static ApiException unavailable(ExtractionDataType type) {
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Chưa nhập được dữ liệu " + type.label()
                + ": dịch vụ " + type.serviceId() + " chưa sẵn sàng hoặc chưa hỗ trợ nhập hàng loạt. "
                + "Vui lòng thử lại sau.");
    }
}
