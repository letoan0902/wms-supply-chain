package vn.wms.notification.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.wms.common.api.ApiResponse;
import vn.wms.notification.client.dto.OrderKpiMetricsResponse;

@Component
public class OrderKpiClient {

    private static final Logger log = LoggerFactory.getLogger(OrderKpiClient.class);
    private final RestClient restClient;

    public OrderKpiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://order-service").build();
    }

    public OrderKpiMetricsResponse fetchMetrics(Long warehouseId) {
        try {
            ApiResponse<OrderKpiMetricsResponse> response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/internal/orders/kpi-metrics");
                        if (warehouseId != null) {
                            uriBuilder.queryParam("warehouseId", warehouseId);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<OrderKpiMetricsResponse>>() {});

            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception ex) {
            log.warn("Không thể lấy KPI từ order-service (warehouseId={}): {}", warehouseId, ex.getMessage());
        }
        return OrderKpiMetricsResponse.empty();
    }
}
