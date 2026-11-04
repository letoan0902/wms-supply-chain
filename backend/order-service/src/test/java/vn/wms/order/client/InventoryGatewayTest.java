package vn.wms.order.client;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Kiểm tra cách InventoryGateway quy lỗi Feign về ApiException, không cần inventory-service thật. */
@ExtendWith(MockitoExtension.class)
class InventoryGatewayTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "/api/inventory/warehouses/9",
            Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private InventoryGateway gateway;

    @Test
    void returnsWarehouseWhenInventoryServiceAnswers() {
        WarehouseSummary warehouse = new WarehouseSummary(5L, "WH-HN", "Kho Hà Nội", "Long Biên", true);
        when(inventoryClient.getWarehouse(5L)).thenReturn(ApiResponse.ok(warehouse));

        assertThat(gateway.findWarehouse(5L)).contains(warehouse);
        assertThat(gateway.requireActiveWarehouse(5L)).isEqualTo(warehouse);
    }

    @Test
    void returnsEmptyForNullIdNotFoundOrErrorResponse() {
        when(inventoryClient.getWarehouse(9L)).thenThrow(new FeignException.NotFound("not found", REQUEST, null, Map.of()));
        when(inventoryClient.getWarehouse(8L)).thenReturn(ApiResponse.error(ErrorCode.NOT_FOUND, "Không tìm thấy kho"));

        assertThat(gateway.findWarehouse(null)).isEmpty();
        assertThat(gateway.findWarehouse(9L)).isEmpty();
        assertThat(gateway.findWarehouse(8L)).isEmpty();
    }

    @Test
    void requireActiveWarehouseRejectsMissingOrInactiveWarehouse() {
        when(inventoryClient.getWarehouse(9L)).thenThrow(new FeignException.NotFound("not found", REQUEST, null, Map.of()));
        when(inventoryClient.getWarehouse(6L))
                .thenReturn(ApiResponse.ok(new WarehouseSummary(6L, "WH-OLD", "Kho cũ", null, false)));

        assertThatThrownBy(() -> gateway.requireActiveWarehouse(9L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> gateway.requireActiveWarehouse(6L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("WH-OLD")
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void mapsTransportFailuresToServiceUnavailable() {
        when(inventoryClient.getWarehouse(7L))
                .thenThrow(new FeignException.ServiceUnavailable("no instance", REQUEST, null, Map.of()));

        assertThatThrownBy(() -> gateway.findWarehouse(7L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
