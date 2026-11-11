package vn.wms.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hợp đồng phía order-service với các endpoint nội bộ của inventory-service (WMS-20.4): đi qua Feign client thật
 * ({@link InventoryClient}, mã hóa và giải mã của Spring Cloud OpenFeign) tới một {@link Client} giả thay cho mạng,
 * kiểm tra đường dẫn, phương thức, thân JSON, header doanh nghiệp và cách {@link InventoryGateway} đọc phản hồi.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryInternalContractTest {

    private static final Long COMPANY = 1L;

    @Autowired
    private InventoryGateway inventoryGateway;

    @Autowired
    private StubInventory stub;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        stub.reset();
        TenantContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Kiểm tra vị trí nhập: POST /internal/inventory/receipts/location-check, rỗng thì cho lập phiếu")
    void receiptLocationCheckPassesWhenNoConflict() throws Exception {
        stub.respond(200, "{\"success\":true,\"code\":\"OK\",\"message\":\"Thành công\",\"data\":[]}");

        inventoryGateway.assertReceiptLocationsNotFrozen(new ReceiptLocationCheckRequest(3L, List.of(
                new ReceiptLocationCheckRequest.Line(101L, 15L, "WH1-A-R01-B01"),
                new ReceiptLocationCheckRequest.Line(102L, null, null))));

        Request sent = stub.last();
        assertThat(sent.httpMethod()).isEqualTo(Request.HttpMethod.POST);
        assertThat(sent.url()).endsWith("/internal/inventory/receipts/location-check");
        assertThat(sent.headers().get(TenantContext.HEADER_COMPANY_ID)).containsExactly(String.valueOf(COMPANY));
        JsonNode body = json.readTree(sent.body());
        assertThat(body.path("warehouseId").asLong()).isEqualTo(3L);
        assertThat(body.path("lines")).hasSize(2);
        assertThat(body.path("lines").get(0).path("productId").asLong()).isEqualTo(101L);
        assertThat(body.path("lines").get(0).path("locationId").asLong()).isEqualTo(15L);
        assertThat(body.path("lines").get(0).path("locationCode").asText()).isEqualTo("WH1-A-R01-B01");
    }

    @Test
    @DisplayName("Kiểm tra vị trí nhập: có xung đột thì 409 nêu mã vị trí và mã đợt kiểm kê")
    void receiptLocationCheckRejectsFrozenLocation() {
        stub.respond(200, "{\"success\":true,\"code\":\"OK\",\"message\":\"Thành công\",\"data\":[{\"sessionId\":7,"
                + "\"sessionCode\":\"ST-202611-01\",\"binLocationId\":15,\"binLocationCode\":\"WH1-A-R01-B01\",\"productId\":101}]}");

        assertThatThrownBy(() -> inventoryGateway.assertReceiptLocationsNotFrozen(new ReceiptLocationCheckRequest(3L,
                List.of(new ReceiptLocationCheckRequest.Line(101L, 15L, null)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("WH1-A-R01-B01")
                .hasMessageContaining("ST-202611-01")
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("Lỗi nghiệp vụ 4xx của inventory-service được trả lại nguyên mã và thông báo; 5xx thành 503")
    void businessErrorsArePassedThroughAndServerErrorsBecomeUnavailable() {
        stub.respond(400, "{\"success\":false,\"code\":\"BAD_REQUEST\","
                + "\"message\":\"Kho 'Kho A' chưa có vị trí ô kệ nào để lưu cất hàng\",\"data\":null}");
        assertThatThrownBy(() -> inventoryGateway.assertReceiptLocationsNotFrozen(new ReceiptLocationCheckRequest(3L,
                List.of(new ReceiptLocationCheckRequest.Line(101L, null, null)))))
                .isInstanceOf(ApiException.class)
                .hasMessage("Kho 'Kho A' chưa có vị trí ô kệ nào để lưu cất hàng")
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);

        stub.respond(500, "{\"success\":false,\"code\":\"INTERNAL_ERROR\",\"message\":\"Lỗi hệ thống\"}");
        assertThatThrownBy(() -> inventoryGateway.assertReceiptLocationsNotFrozen(new ReceiptLocationCheckRequest(3L,
                List.of(new ReceiptLocationCheckRequest.Line(101L, null, null)))))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Giữ chỗ: POST /internal/inventory/reservations với orderId, warehouseId, items; đọc được kết quả")
    void reserveOrderSendsOrderLinesAndReadsResult() throws Exception {
        stub.respond(200, "{\"success\":true,\"code\":\"OK\",\"message\":\"Thành công\",\"data\":{\"orderId\":55,"
                + "\"alreadyReserved\":true,\"lines\":[{\"productId\":101,\"reservedQuantity\":6.000}]}}");

        ReserveOrderResponse result = inventoryGateway.reserveForOrder(new ReserveOrderRequest(55L, "SO-20261111-0001", 3L,
                List.of(new ReserveOrderRequest.Item(101L, "SKU-1", new java.math.BigDecimal("6.000")))));

        assertThat(result.alreadyReserved()).isTrue();
        assertThat(result.lines().get(0).reservedQuantity()).isEqualByComparingTo("6");
        Request sent = stub.last();
        assertThat(sent.httpMethod()).isEqualTo(Request.HttpMethod.POST);
        assertThat(sent.url()).endsWith("/internal/inventory/reservations");
        JsonNode body = json.readTree(sent.body());
        assertThat(body.path("orderId").asLong()).isEqualTo(55L);
        assertThat(body.path("orderNumber").asText()).isEqualTo("SO-20261111-0001");
        assertThat(body.path("warehouseId").asLong()).isEqualTo(3L);
        assertThat(body.path("items").get(0).path("productId").asLong()).isEqualTo(101L);
        assertThat(body.path("items").get(0).path("skuCode").asText()).isEqualTo("SKU-1");
        assertThat(body.path("items").get(0).path("quantity").decimalValue()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("Giữ chỗ: 409 thiếu hàng thành StockShortageException mang thông báo và danh sách thiếu; 503 thành SERVICE_UNAVAILABLE")
    void reserveOrderShortageAndUnavailable() {
        stub.respond(409, "{\"success\":false,\"code\":\"CONFLICT\",\"message\":\"Không đủ tồn khả dụng để giữ chỗ cho đơn "
                + "SO-1: SKU SKU-1 thiếu 2 (yêu cầu 5, giữ được 3)\",\"data\":[{\"productId\":101,\"skuCode\":\"SKU-1\","
                + "\"requestedQuantity\":5,\"availableQuantity\":3,\"shortageQuantity\":2}]}");
        ReserveOrderRequest request = new ReserveOrderRequest(56L, "SO-1", 3L,
                List.of(new ReserveOrderRequest.Item(101L, "SKU-1", new java.math.BigDecimal("5"))));

        assertThatThrownBy(() -> inventoryGateway.reserveForOrder(request))
                .isInstanceOf(StockShortageException.class)
                .hasMessageContaining("SKU-1 thiếu 2")
                .satisfies(ex -> assertThat(((StockShortageException) ex).getShortages()).isInstanceOf(List.class));

        stub.respond(503, "");
        assertThatThrownBy(() -> inventoryGateway.reserveForOrder(request))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    /** {@link Client} giả: ghi lại request và trả phản hồi đặt trước, thay cho inventory-service qua mạng. */
    static class StubInventory {
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private volatile int status = 200;
        private volatile String body = "{}";

        void reset() {
            requests.clear();
            respond(200, "{}");
        }

        void respond(int status, String body) {
            this.status = status;
            this.body = body;
        }

        Request last() {
            assertThat(requests).isNotEmpty();
            return requests.get(requests.size() - 1);
        }

        List<Request> requests() {
            return requests;
        }

        Response execute(Request request, Request.Options options) {
            requests.add(request);
            return Response.builder()
                    .status(status)
                    .reason(status < 400 ? "OK" : "Error")
                    .request(request)
                    .headers(Map.of("Content-Type", List.of("application/json;charset=UTF-8")))
                    .body(body, StandardCharsets.UTF_8)
                    .build();
        }
    }

    @TestConfiguration
    static class StubConfig {
        @Bean
        StubInventory stubInventory() {
            return new StubInventory();
        }

        /** Feign dùng {@link Client} có trong context thay cho client cân bằng tải, nên không cần mạng hay Eureka. */
        @Bean
        Client stubFeignClient(StubInventory stubInventory) {
            return stubInventory::execute;
        }
    }
}
