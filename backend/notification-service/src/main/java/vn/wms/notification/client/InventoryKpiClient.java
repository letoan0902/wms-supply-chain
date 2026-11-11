package vn.wms.notification.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.wms.common.api.ApiResponse;
import vn.wms.notification.client.dto.InventoryKpiMetricsResponse;

@Component
public class InventoryKpiClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryKpiClient.class);
    private final RestClient restClient;

    public InventoryKpiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://inventory-service").build();
    }

    public InventoryKpiMetricsResponse fetchMetrics(Long warehouseId) {
        try {
            ApiResponse<InventoryKpiMetricsResponse> response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/inventory/internal/kpi-metrics");
                        if (warehouseId != null) {
                            uriBuilder.queryParam("warehouseId", warehouseId);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<InventoryKpiMetricsResponse>>() {});

            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception ex) {
            log.warn("Không thể lấy KPI từ inventory-service (warehouseId={}): {}", warehouseId, ex.getMessage());
        }
        return InventoryKpiMetricsResponse.empty();
    }
}
