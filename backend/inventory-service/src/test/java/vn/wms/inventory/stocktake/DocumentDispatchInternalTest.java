package vn.wms.inventory.stocktake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.internal.DocumentDispatchRequest;
import vn.wms.inventory.dto.internal.DocumentDispatchResponse;
import vn.wms.inventory.dto.internal.ReserveOrderRequest;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.messaging.DispatchStockHandler;
import vn.wms.inventory.repository.StockDispatchRepository;
import vn.wms.inventory.service.DocumentDispatchService;
import vn.wms.inventory.service.StockReservationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trừ tồn đồng bộ khi hoàn tất phiếu xuất (WMS-20.4, TC-LOAD-01.5): endpoint nội bộ
 * {@code POST /internal/inventory/dispatches} trừ mỗi khóa một lần, không đủ tồn thì không trừ gì và cho thử lại,
 * listener {@code order.dispatched} mang cùng khóa thì bỏ qua; xuất một phần vẫn giữ chỗ phần còn lại.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentDispatchInternalTest extends StocktakeTestSupport {

    private static final String URL = "/internal/inventory/dispatches";

    @Autowired
    private StockReservationService stockReservationService;
    @Autowired
    private DocumentDispatchService documentDispatchService;
    @Autowired
    private DispatchStockHandler dispatchStockHandler;
    @Autowired
    private StockDispatchRepository stockDispatchRepository;

    @AfterEach
    void cleanDispatches() {
        for (Long company : List.of(COMPANY_A, COMPANY_B)) {
            TenantContext.runAs(company, () -> stockDispatchRepository.deleteAll());
        }
    }

    private String key() {
        return "GDN-T" + System.nanoTime();
    }

    private DocumentDispatchRequest dispatch(String key, long orderId, Long warehouseId, String quantity) {
        return new DocumentDispatchRequest(key, orderId, "SO-D-" + orderId, warehouseId, "thukho",
                List.of(new DocumentDispatchRequest.Item(PRODUCT_1, "SKU-1", new BigDecimal(quantity))));
    }

    private void reserve(long orderId, String quantity) {
        TenantContext.runAs(COMPANY_A, () -> stockReservationService.reserveForOrder(new ReserveOrderRequest(orderId,
                "SO-D-" + orderId, warehouse1.getId(),
                List.of(new ReserveOrderRequest.Item(PRODUCT_1, "SKU-1", new BigDecimal(quantity))))));
    }

    private BigDecimal reserved(Long stockItemId) {
        return TenantContext.callAs(COMPANY_A, () -> stockItemRepository.findById(stockItemId).orElseThrow().getReservedQuantity());
    }

    @Test
    @DisplayName("Hợp đồng: trừ tồn và hoàn tất giữ chỗ của đơn; gọi lại cùng khóa trả alreadyProcessed, không trừ thêm")
    void deductsOncePerKey() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        reserve(100L, "4");
        String key = key();

        postJson(URL, dispatch(key, 100L, warehouse1.getId(), "4.000"), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dispatchKey", is(key)))
                .andExpect(jsonPath("$.data.alreadyProcessed", is(false)))
                .andExpect(jsonPath("$.data.totalDeductedQuantity", is(4.0)));
        postJson(URL, dispatch(key, 100L, warehouse1.getId(), "4.000"), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyProcessed", is(true)))
                .andExpect(jsonPath("$.data.orderId", is(100)))
                .andExpect(jsonPath("$.data.totalDeductedQuantity", nullValue()));

        assertThat(onHand(COMPANY_A, item.getId())).isEqualByComparingTo("6.000");
        assertThat(reserved(item.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Listener order.dispatched mang cùng khóa thì bỏ qua; sự kiện cũ không có khóa vẫn trừ theo messageId")
    void listenerSkipsAlreadyDeductedKey() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        String key = key();
        postJson(URL, dispatch(key, 101L, warehouse1.getId(), "3"), COMPANY_A, null).andExpect(status().isOk());

        List<OrderLine> lines = List.of(new OrderLine(PRODUCT_1, "SKU-1", "SP 1", new BigDecimal("3"), "cái", BigDecimal.ONE));
        OrderDispatchedEvent withKey = new OrderDispatchedEvent(101L, "SO-D-101", OrderType.SALES, warehouse1.getId(),
                "thukho", Instant.now(), lines, key);
        TenantContext.runAs(COMPANY_A, () -> dispatchStockHandler.onOrderDispatched(withKey, "msg-" + key));
        assertThat(onHand(COMPANY_A, item.getId())).isEqualByComparingTo("7.000");

        OrderDispatchedEvent legacy = new OrderDispatchedEvent(102L, "SO-D-102", OrderType.SALES, warehouse1.getId(),
                "thukho", Instant.now(), lines);
        TenantContext.runAs(COMPANY_A, () -> dispatchStockHandler.onOrderDispatched(legacy, "msg-legacy-" + key));
        assertThat(onHand(COMPANY_A, item.getId())).isEqualByComparingTo("4.000");
    }

    @Test
    @DisplayName("Không đủ tồn: lỗi 400 nêu tồn thực tế, không trừ, không ghi khóa; có hàng rồi gửi lại cùng khóa thì trừ được")
    void insufficientStockDeductsNothingAndCanBeRetried() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "5.000", "0");
        String key = key();

        postJson(URL, dispatch(key, 103L, warehouse1.getId(), "8"), COMPANY_A, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Không đủ tồn kho để xuất")));
        assertThat(onHand(COMPANY_A, item.getId())).isEqualByComparingTo("5.000");
        assertThat(TenantContext.callAs(COMPANY_A, () -> stockDispatchRepository.existsByMessageId(key))).isFalse();

        stock(COMPANY_A, binA2, PRODUCT_1, null, "5.000", "0");
        postJson(URL, dispatch(key, 103L, warehouse1.getId(), "8"), COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyProcessed", is(false)));
    }

    @Test
    @DisplayName("Xuất một phần: chỉ hoàn tất phần giữ chỗ đã xuất, phần còn lại của đơn vẫn được giữ")
    void partialDispatchKeepsRemainderReserved() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        reserve(104L, "8");

        postJson(URL, dispatch(key(), 104L, warehouse1.getId(), "3"), COMPANY_A, null).andExpect(status().isOk());

        assertThat(onHand(COMPANY_A, item.getId())).isEqualByComparingTo("7.000");
        assertThat(reserved(item.getId())).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("Hai lời gọi đồng thời cùng khóa (gửi lại khi lần đầu hết thời gian chờ): chỉ trừ một lần, cả hai đều thành công")
    void concurrentCallsWithSameKeyDeductOnce() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        String key = key();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<DocumentDispatchResponse> results = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        results.add(TenantContext.callAs(COMPANY_A,
                                () -> documentDispatchService.dispatch(dispatch(key, 105L, warehouse1.getId(), "4"))));
                    } catch (Exception ex) {
                        errors.add(ex.toString());
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(errors).isEmpty();
        assertThat(results).extracting(DocumentDispatchResponse::alreadyProcessed).containsExactlyInAnyOrder(true, false);
        assertThat(onHand(COMPANY_A, item.getId())).isEqualByComparingTo("6.000");
    }

    @Test
    @DisplayName("Tách doanh nghiệp: khóa trùng giá trị ở doanh nghiệp khác là lần trừ riêng; B không trừ được kho của A")
    void isolatedPerCompany() throws Exception {
        StockItem itemA = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        StockItem itemB = stock(COMPANY_B, binOfCompanyB, PRODUCT_1, null, "10.000", "0");
        String key = key();
        postJson(URL, dispatch(key, 106L, warehouse1.getId(), "2"), COMPANY_A, null).andExpect(status().isOk());

        postJson(URL, dispatch(key, 106L, warehouseB.getId(), "2"), COMPANY_B, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyProcessed", is(false)));
        postJson(URL, dispatch(key(), 107L, warehouse1.getId(), "1"), COMPANY_B, null)
                .andExpect(status().isNotFound());

        assertThat(onHand(COMPANY_A, itemA.getId())).isEqualByComparingTo("8.000");
        assertThat(onHand(COMPANY_B, itemB.getId())).isEqualByComparingTo("8.000");
    }
}
