package vn.wms.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Đọc dữ liệu của service khác cho trợ lý AI và bản tóm tắt dashboard, qua client cân bằng tải của
 * {@code ServiceClientConfig}: mỗi lời gọi mang nguyên X-Company-Id, X-User-Name, X-User-Roles, X-User-Warehouse-Ids,
 * X-Plan của người hỏi, nên bộ lọc doanh nghiệp, phạm vi kho và kiểm tra vai trò của service phía sau áp dụng y như
 * khi người đó tự mở màn hình.
 *
 * <p>Lớp này CHỈ có lời gọi GET: trợ lý không ghi dữ liệu. Lỗi không ném ra ngoài mà trả {@link Result} để trợ lý
 * trả lời thành câu (không tìm thấy, không có quyền, dịch vụ chưa sẵn sàng).
 */
@Component
public class ServiceDataClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceDataClient.class);

    /** Câu trả lời khi service phía sau từ chối vai trò hoặc phạm vi kho của người hỏi. */
    public static final String FORBIDDEN_MESSAGE = "Bạn không có quyền xem dữ liệu này.";

    /** Kết quả một lần đọc. */
    public enum Status {
        OK, NOT_FOUND, FORBIDDEN, BAD_REQUEST, UNAVAILABLE
    }

    /**
     * @param status  kết quả
     * @param data    trường {@code data} của {@code ApiResponse} khi {@link Status#OK}
     * @param message thông báo tiếng Việt khi không thành công
     */
    public record Result(Status status, JsonNode data, String message) {

        public boolean ok() {
            return status == Status.OK;
        }
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ServiceDataClient(RestClient.Builder loadBalancedRestClientBuilder, ObjectMapper objectMapper) {
        this.restClient = loadBalancedRestClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Gọi GET {@code http://{serviceId}{path}} với tham số đường dẫn và tham số truy vấn (bỏ qua giá trị {@code null}),
     * mọi giá trị đều được mã hóa URL.
     *
     * @param serviceId  tên service trên Eureka, ví dụ {@code inventory-service}
     * @param path       đường dẫn có thể chứa biến, ví dụ {@code /api/products/sku/{sku}}
     * @param pathValues giá trị của các biến trong {@code path}
     * @param query      tham số truy vấn theo thứ tự
     */
    public Result get(String serviceId, String path, Map<String, ?> pathValues, Map<String, ?> query) {
        Map<String, Object> variables = new LinkedHashMap<>(pathValues);
        StringBuilder uri = new StringBuilder("http://").append(serviceId).append(path);
        char separator = '?';
        for (Map.Entry<String, ?> entry : query.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String variable = "q_" + entry.getKey();
            uri.append(separator).append(entry.getKey()).append("={").append(variable).append('}');
            variables.put(variable, entry.getValue());
            separator = '&';
        }
        try {
            JsonNode body = restClient.get().uri(uri.toString(), variables).retrieve().body(JsonNode.class);
            if (body == null || !body.has("data")) {
                log.warn("{} trả phản hồi không đúng dạng ApiResponse cho {}", serviceId, path);
                return unavailable(serviceId);
            }
            return new Result(Status.OK, body.get("data"), null);
        } catch (RestClientResponseException ex) {
            return translate(serviceId, path, ex);
        } catch (RestClientException | IllegalStateException ex) {
            // IllegalStateException: Spring Cloud LoadBalancer không tìm thấy instance nào của service.
            log.warn("Không gọi được {} {}: {}", serviceId, path, ex.getClass().getSimpleName());
            return unavailable(serviceId);
        }
    }

    private Result translate(String serviceId, String path, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String message = readMessage(ex);
        if (status == 404) {
            return new Result(Status.NOT_FOUND, null, message);
        }
        if (status == 401 || status == 403) {
            return new Result(Status.FORBIDDEN, null, FORBIDDEN_MESSAGE);
        }
        if (status == 400 || status == 409 || status == 422) {
            return new Result(Status.BAD_REQUEST, null, message != null ? message : "Yêu cầu tra cứu không hợp lệ.");
        }
        log.warn("{} trả HTTP {} cho {}", serviceId, status, path);
        return unavailable(serviceId);
    }

    private String readMessage(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            if (body != null && body.hasNonNull("message")) {
                return body.get("message").asText();
            }
        } catch (Exception parseError) {
            // Body không phải ApiResponse: không có thông báo.
        }
        return null;
    }

    private static Result unavailable(String serviceId) {
        return new Result(Status.UNAVAILABLE, null, "Chưa tra cứu được: dịch vụ " + serviceId
                + " chưa sẵn sàng, vui lòng thử lại sau.");
    }
}
