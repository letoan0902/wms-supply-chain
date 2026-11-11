package vn.wms.inventory.stocktake;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.cache.FakeStringRedis;
import vn.wms.inventory.cache.StockCacheKeys;
import vn.wms.inventory.cache.StockTotalsCache;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.LockLotRequest;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.service.LotService;
import vn.wms.inventory.service.StockDeductionService;
import vn.wms.inventory.service.StockReceiptService;
import vn.wms.inventory.service.StockReservationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp bộ nhớ đệm tổng tồn và tồn khả dụng (WMS-19.1) với Redis giả lập trong bộ nhớ: API đọc dùng bộ
 * nhớ đệm, và SAU MỖI đường thay đổi tồn (giữ chỗ, giải phóng, trừ tồn, xuất hàng theo sự kiện, nhập kho theo GRN,
 * xuất và nhận điều chuyển, duyệt điều chỉnh kiểm kê, khóa và mở khóa lô) API trả ngay số mới. Ngoài ra: giao dịch
 * rollback không vô hiệu, quyết định trừ tồn không đọc bộ nhớ đệm, hai doanh nghiệp không đọc số của nhau.
 *
 * <p>Đặt trong gói kiểm kê để dùng lại dữ liệu mẫu hai doanh nghiệp, các tài khoản theo vai trò và tiện ích HTTP của
 * {@link StocktakeTestSupport}.
 */
@SpringBootTest(properties = "app.stock-cache.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StockCacheIntegrationTest.FakeRedisConfig.class)
class StockCacheIntegrationTest extends StocktakeTestSupport {

    private static final String STOCKTAKES = "/api/inventory/stocktakes";

    @TestConfiguration
    static class FakeRedisConfig {
        @Bean
        FakeStringRedis fakeStringRedis() {
            return new FakeStringRedis(System::currentTimeMillis);
        }

        @Bean
        @Primary
        StringRedisTemplate fakeStringRedisTemplate(FakeStringRedis fake) {
            return fake.template();
        }
    }

    @MockBean
    private WmsEventPublisher wmsEventPublisher;

    @Autowired
    private FakeStringRedis fakeRedis;
    @Autowired
    private StockTotalsCache stockTotalsCache;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StockReservationService stockReservationService;
    @Autowired
    private StockDeductionService stockDeductionService;
    @Autowired
    private StockReceiptService stockReceiptService;
    @Autowired
    private LotService lotService;

    @BeforeEach
    void resetFakeRedis() {
        fakeRedis.clear();
    }

    // ------------------------------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------------------------------

    private JsonNode atp(Long company, Long productId, Long warehouseId) throws Exception {
        String url = "/api/inventory/stock/atp?productId=" + productId + (warehouseId != null ? "&warehouseId=" + warehouseId : "");
        return data(getAs(url, company, null).andExpect(status().isOk()));
    }

    private void assertAtp(Long company, Long productId, Long warehouseId, String onHand, String reserved) throws Exception {
        JsonNode node = atp(company, productId, warehouseId);
        assertThat(node.path("onHandQuantity").decimalValue()).isEqualByComparingTo(onHand);
        assertThat(node.path("reservedQuantity").decimalValue()).isEqualByComparingTo(reserved);
        BigDecimal expectedAtp = new BigDecimal(onHand).subtract(new BigDecimal(reserved)).max(BigDecimal.ZERO);
        assertThat(node.path("atpQuantity").decimalValue()).isEqualByComparingTo(expectedAtp);
    }

    /** Sửa tồn bằng SQL gốc, vượt qua JPA nên KHÔNG vô hiệu bộ nhớ đệm: dùng để chứng minh API đang trả số đệm. */
    private void changeBehindTheCache(Long stockItemId, String quantity) {
        jdbcTemplate.update("UPDATE stock_items SET quantity = ? WHERE id = ?", new BigDecimal(quantity), stockItemId);
    }

    private String generation(Long company, Long productId) {
        return fakeRedis.get(StockCacheKeys.productGeneration(company, productId));
    }

    private DeductStockRequest deduct(Long warehouseId, Long binId, Long productId, String quantity, String orderNumber) {
        return new DeductStockRequest(null, orderNumber, warehouseId, "MANUAL", null,
                List.of(new DeductStockItemRequest(productId, "SKU-" + productId, new BigDecimal(quantity), binId, null)));
    }

    private OrderCreatedEvent salesOrder(long orderId, Long warehouseId, Long productId, String quantity) {
        return new OrderCreatedEvent(orderId, "SO-CACHE-" + orderId, OrderType.SALES, "CONFIRMED", null, 2001L,
                warehouseId, "Kho kiểm kê 1", new BigDecimal("1000.00"), "sales01", Instant.now(),
                List.of(new OrderLine(productId, "SKU-" + productId, "Hàng thử", new BigDecimal(quantity), "Cái",
                        new BigDecimal("10.00"))));
    }

    // ------------------------------------------------------------------------------------------
    // Đọc qua bộ nhớ đệm
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ATP đơn, ATP theo lô và KPI được phục vụ từ bộ nhớ đệm cho tới khi tồn đổi qua nghiệp vụ")
    void readEndpointsServeCachedFiguresUntilStockChanges() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        stock(COMPANY_A, binA2, PRODUCT_2, null, "4.000", "0");
        long hitsBefore = stockTotalsCache.hitCount();

        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "10", "0");
        assertAtp(COMPANY_A, PRODUCT_1, null, "10", "0");
        postJson("/api/inventory/stock/atp/batch",
                Map.of("productIds", List.of(PRODUCT_2, PRODUCT_1, PRODUCT_2), "warehouseId", warehouse1.getId()),
                COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", is(2)))
                .andExpect(jsonPath("$.data.items[0].productId", is(PRODUCT_2.intValue())))
                .andExpect(jsonPath("$.data.items[0].atpQuantity", is(4.0)))
                .andExpect(jsonPath("$.data.items[1].productId", is(PRODUCT_1.intValue())))
                .andExpect(jsonPath("$.data.items[1].atpQuantity", is(10.0)));
        getAs("/internal/inventory/kpi-metrics?warehouseId=" + warehouse1.getId(), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStockQuantity", is(14.0)))
                .andExpect(jsonPath("$.data.availableStockQuantity", is(14.0)))
                .andExpect(jsonPath("$.data.occupiedLocations", is(2)))
                .andExpect(jsonPath("$.data.totalLocations", is(3)));

        // Đổi tồn bằng SQL gốc (không qua JPA): API vẫn trả số đệm, chứng tỏ không truy vấn lại cơ sở dữ liệu
        changeBehindTheCache(item.getId(), "999");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "10", "0");
        assertAtp(COMPANY_A, PRODUCT_1, null, "10", "0");
        getAs("/internal/inventory/kpi-metrics?warehouseId=" + warehouse1.getId(), COMPANY_A, null)
                .andExpect(jsonPath("$.data.totalStockQuantity", is(14.0)));
        assertThat(stockTotalsCache.hitCount()).isGreaterThanOrEqualTo(hitsBefore + 3);

        // Đổi tồn qua nghiệp vụ (trừ 1 trên số thật 999): số trả về là số mới ngay sau commit
        TenantContext.runAs(COMPANY_A, () -> stockDeductionService.deductStock(
                deduct(warehouse1.getId(), binA1.getId(), PRODUCT_1, "1", "M-CACHE-1"), "tester"));
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "998", "0");
        assertAtp(COMPANY_A, PRODUCT_1, null, "998", "0");
        getAs("/internal/inventory/kpi-metrics?warehouseId=" + warehouse1.getId(), COMPANY_A, null)
                .andExpect(jsonPath("$.data.totalStockQuantity", is(1002.0)));
        assertThat(fakeRedis.keys()).contains(
                "wms:stock:1:p:" + PRODUCT_1 + ":w:" + warehouse1.getId(),
                "wms:stock:1:p:" + PRODUCT_1 + ":w:all",
                "wms:stock:1:kpi:" + warehouse1.getId());
    }

    // ------------------------------------------------------------------------------------------
    // Vô hiệu sau từng đường thay đổi tồn
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Giữ chỗ cho đơn bán rồi giải phóng khi hủy: ATP đổi ngay sau mỗi bước")
    void reservationAndReleaseEvict() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "100", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "100", "0");

        TenantContext.runAs(COMPANY_A, () ->
                stockReservationService.reserveStock(salesOrder(7001L, warehouse1.getId(), PRODUCT_1, "35"), "msg-cache-res-1"));
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "100", "35");

        TenantContext.runAs(COMPANY_A, () -> stockReservationService.releaseReservationsByOrderId(7001L));
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "100", "0");
    }

    @Test
    @DisplayName("Trừ tồn thủ công và xuất hàng theo sự kiện order.dispatched: tồn mới ngay sau commit")
    void deductionAndDispatchEvict() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "50", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "50", "0");

        TenantContext.runAs(COMPANY_A, () -> stockDeductionService.deductStock(
                deduct(warehouse1.getId(), binA1.getId(), PRODUCT_1, "20", "M-CACHE-2"), "tester"));
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "30", "0");

        TenantContext.runAs(COMPANY_A, () -> stockDeductionService.deductStockForDispatchEvent(
                deduct(warehouse1.getId(), binA1.getId(), PRODUCT_1, "5", "SO-CACHE-DISPATCH"), "tester", "msg-cache-dispatch-1"));
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "25", "0");
    }

    @Test
    @DisplayName("Nhập kho theo phiếu GRN (order.received): tồn mới ngay sau commit")
    void receiptEvicts() throws Exception {
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "0", "0");

        ReceivedItemLine line = new ReceivedItemLine(PRODUCT_1, "SKU-" + PRODUCT_1, "Hàng thử", new BigDecimal("12.000"),
                BigDecimal.ZERO, "Cái", binA1.getId(), binA1.getCode(), "LOT-CACHE-1", null, null);
        OrderReceivedEvent event = new OrderReceivedEvent(88001L, "GRN-CACHE-0001", null, null, warehouse1.getId(),
                10L, "staff01", LocalDate.now(), Instant.now(), List.of(line));
        TenantContext.runAs(COMPANY_A, () -> stockReceiptService.processOrderReceived(event, "msg-cache-grn-1"));

        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "12", "0");
        assertAtp(COMPANY_A, PRODUCT_1, null, "12", "0");
    }

    @Test
    @DisplayName("Điều chuyển: duyệt xuất giảm tồn kho nguồn, nhận hàng tăng tồn kho đích, số đệm của cả hai kho đổi ngay")
    void transferOutAndInEvict() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "40", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "40", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse2.getId(), "0", "0");

        CreateStockTransferRequest create = new CreateStockTransferRequest(warehouse1.getId(), warehouse2.getId(),
                Instant.now(), "Điều chuyển thử bộ nhớ đệm",
                List.of(new CreateStockTransferItemRequest(PRODUCT_1, "SKU-" + PRODUCT_1, "Hàng thử", "Cái",
                        binA1.getId(), new BigDecimal("15"), null)));
        JsonNode created = data(postJson("/api/inventory/transfers", create, COMPANY_A, null).andExpect(status().isCreated()));
        long transferId = created.path("id").asLong();
        long itemId = created.path("items").get(0).path("id").asLong();

        postJson("/api/inventory/transfers/" + transferId + "/dispatch", null, COMPANY_A, null).andExpect(status().isOk());
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "25", "0");

        ReceiveStockTransferRequest receive = new ReceiveStockTransferRequest(
                List.of(new ReceiveStockTransferItemRequest(itemId, new BigDecimal("15"), BigDecimal.ZERO, binW2.getId(), null)),
                null);
        postJson("/api/inventory/transfers/" + transferId + "/receive", receive, COMPANY_A, null).andExpect(status().isOk());
        assertAtp(COMPANY_A, PRODUCT_1, warehouse2.getId(), "15", "0");
        assertAtp(COMPANY_A, PRODUCT_1, null, "40", "0");
    }

    @Test
    @DisplayName("Duyệt phiếu điều chỉnh sau kiểm kê: tồn mới ngay sau khi Kế toán duyệt")
    void stocktakeAdjustmentApprovalEvicts() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "10", "0");

        long id = createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);
        postJson(STOCKTAKES + "/" + id + "/counts", countBody(binA1.getId(), PRODUCT_1, null, "7.5", "SET"), COMPANY_A, staff1)
                .andExpect(status().isOk());
        postJson(STOCKTAKES + "/" + id + "/finish", Map.of("confirmUncountedAsZero", true), COMPANY_A, manager1)
                .andExpect(status().isOk());
        postJson(STOCKTAKES + "/" + id + "/approve", null, COMPANY_A, manager1).andExpect(status().isOk());
        // Duyệt cấp Quản lý kho chưa đổi tồn
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "10", "0");

        postJson(STOCKTAKES + "/" + id + "/adjustment/approve", null, COMPANY_A, accountant).andExpect(status().isOk());
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "7.5", "0");
    }

    @Test
    @DisplayName("Khóa lô loại tồn khỏi ATP, mở khóa đưa tồn trở lại: số đệm đổi ngay")
    void lotLockAndUnlockEvict() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "9", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "9", "0");

        TenantContext.runAs(COMPANY_A, () -> lotService.lockLot(item.getId(), new LockLotRequest("Cách ly kiểm định"), "manager01"));
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "0", "0");

        TenantContext.runAs(COMPANY_A, () -> lotService.unlockLot(item.getId(), "manager01"));
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "9", "0");
    }

    // ------------------------------------------------------------------------------------------
    // Đúng đắn
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Giao dịch trừ tồn bị từ chối và rollback thì không vô hiệu bộ nhớ đệm")
    void rolledBackChangeDoesNotEvict() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "5", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "5", "0");
        String generationBefore = generation(COMPANY_A, PRODUCT_1);

        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_A, () -> stockDeductionService.deductStock(
                deduct(warehouse1.getId(), binA1.getId(), PRODUCT_1, "6", "M-CACHE-ROLLBACK"), "tester")))
                .isInstanceOf(ApiException.class);

        assertThat(generation(COMPANY_A, PRODUCT_1)).isEqualTo(generationBefore);
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "5", "0");
    }

    @Test
    @DisplayName("Quyết định trừ tồn đọc cơ sở dữ liệu dưới khóa, không đọc số đệm (kể cả khi số đệm lớn hơn)")
    void stockDecisionsIgnoreCache() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "10", "0");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "10", "0");
        // Dựng một số đệm sai (1000) với đúng thế hệ hiện tại: API đọc trả 1000
        String currentGeneration = generation(COMPANY_A, PRODUCT_1) != null ? generation(COMPANY_A, PRODUCT_1) : "0";
        fakeRedis.put(StockCacheKeys.productTotals(COMPANY_A, PRODUCT_1, warehouse1.getId()),
                currentGeneration + "|" + LocalDate.now() + "|1000.000|0.000");
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "1000", "0");

        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_A, () -> stockDeductionService.deductStock(
                deduct(warehouse1.getId(), binA1.getId(), PRODUCT_1, "50", "M-CACHE-DECISION"), "tester")))
                .isInstanceOf(ApiException.class);
        TenantContext.runAs(COMPANY_A, () ->
                stockReservationService.reserveStock(salesOrder(7002L, warehouse1.getId(), PRODUCT_1, "50"), "msg-cache-res-2"));
        // Giữ chỗ chỉ giữ được 10 có thật; số đệm sai bị vô hiệu sau commit
        assertAtp(COMPANY_A, PRODUCT_1, warehouse1.getId(), "10", "10");
    }

    @Test
    @DisplayName("Hai doanh nghiệp trùng ID sản phẩm: mỗi bên chỉ thấy số của mình, kể cả khi bên kia đã có số đệm")
    void companiesNeverReadEachOthersCachedFigures() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "10", "0");
        stock(COMPANY_B, binOfCompanyB, PRODUCT_1, null, "3", "0");

        assertAtp(COMPANY_A, PRODUCT_1, null, "10", "0");
        assertAtp(COMPANY_B, PRODUCT_1, null, "3", "0");
        assertAtp(COMPANY_A, PRODUCT_1, null, "10", "0");
        assertAtp(COMPANY_B, PRODUCT_1, null, "3", "0");
        // B hỏi theo ID kho của A: kho không thuộc B nên tồn 0, không lộ số đệm của A
        assertAtp(COMPANY_B, PRODUCT_1, warehouse1.getId(), "0", "0");

        assertThat(fakeRedis.keys()).contains(
                "wms:stock:1:p:" + PRODUCT_1 + ":w:all",
                "wms:stock:2:p:" + PRODUCT_1 + ":w:all");

        // Thay đổi tồn của A chỉ vô hiệu số của A
        String generationOfB = generation(COMPANY_B, PRODUCT_1);
        TenantContext.runAs(COMPANY_A, () -> stockDeductionService.deductStock(
                deduct(warehouse1.getId(), binA1.getId(), PRODUCT_1, "4", "M-CACHE-ISO"), "tester"));
        assertThat(generation(COMPANY_B, PRODUCT_1)).isEqualTo(generationOfB);
        assertAtp(COMPANY_A, PRODUCT_1, null, "6", "0");
        assertAtp(COMPANY_B, PRODUCT_1, null, "3", "0");

        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("productIds", List.of(PRODUCT_1));
        postJson("/api/inventory/stock/atp/batch", batch, COMPANY_B, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].onHandQuantity", is(3.0)));
    }
}
