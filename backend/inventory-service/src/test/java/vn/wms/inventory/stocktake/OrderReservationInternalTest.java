package vn.wms.inventory.stocktake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.internal.ReserveOrderRequest;
import vn.wms.inventory.dto.internal.ReserveOrderResponse;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.service.StockReservationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giữ chỗ đồng bộ khi xác nhận đơn bán (WMS-20.4, TC-LOAD-01.6): endpoint nội bộ
 * {@code POST /internal/inventory/reservations} giữ tất cả hoặc không, idempotent theo đơn, an toàn khi nhiều đơn
 * xác nhận cùng lúc; listener {@code order.created} không giữ lần hai.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderReservationInternalTest extends StocktakeTestSupport {

    private static final String URL = "/internal/inventory/reservations";

    @Autowired
    private StockReservationService stockReservationService;

    private ReserveOrderRequest request(long orderId, Long warehouseId, Long productId, String quantity) {
        return new ReserveOrderRequest(orderId, "SO-R-" + orderId, warehouseId,
                List.of(new ReserveOrderRequest.Item(productId, "SKU-" + productId, new BigDecimal(quantity))));
    }

    private BigDecimal reservedOf(Long company, Long stockItemId) {
        return TenantContext.callAs(company, () -> stockItemRepository.findById(stockItemId).orElseThrow().getReservedQuantity());
    }

    private List<StockReservation> activeOf(long orderId) {
        return TenantContext.callAs(COMPANY_A, () -> stockReservationRepository.findByOrderIdAndStatus(orderId, "ACTIVE"));
    }

    @Test
    @DisplayName("Hợp đồng: giữ đủ thì 200 kèm số đã giữ theo sản phẩm; gọi lại cùng đơn không giữ thêm (alreadyReserved)")
    void reservesWholeOrderAndIsIdempotentPerOrder() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("productId", PRODUCT_1);
        line.put("skuCode", "SKU-1");
        line.put("quantity", new BigDecimal("6.000"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", 5001L);
        body.put("orderNumber", "SO-R-5001");
        body.put("warehouseId", warehouse1.getId());
        body.put("items", List.of(line));

        postJson(URL, body, COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId", is(5001)))
                .andExpect(jsonPath("$.data.alreadyReserved", is(false)))
                .andExpect(jsonPath("$.data.lines[0].productId", is(PRODUCT_1.intValue())))
                .andExpect(jsonPath("$.data.lines[0].reservedQuantity", is(6.0)));
        postJson(URL, body, COMPANY_A, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyReserved", is(true)))
                .andExpect(jsonPath("$.data.lines[0].reservedQuantity", is(6.0)));

        assertThat(reservedOf(COMPANY_A, item.getId())).isEqualByComparingTo("6.000");
        assertThat(activeOf(5001L)).hasSize(1);
    }

    @Test
    @DisplayName("Thiếu hàng: 409 nêu SKU và phần thiếu, không để lại giữ chỗ một phần cho dòng nào")
    void shortageRejectsWholeOrderWithoutPartialReservation() throws Exception {
        StockItem item1 = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        StockItem item2 = stock(COMPANY_A, binA2, PRODUCT_2, null, "3.000", "0");
        ReserveOrderRequest twoLines = new ReserveOrderRequest(5002L, "SO-R-5002", warehouse1.getId(), List.of(
                new ReserveOrderRequest.Item(PRODUCT_1, "SKU-1", new BigDecimal("4.000")),
                new ReserveOrderRequest.Item(PRODUCT_2, "SKU-2", new BigDecimal("5.000"))));

        postJson(URL, twoLines, COMPANY_A, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message", containsString("SKU-2 thiếu 2")))
                .andExpect(jsonPath("$.data[0].productId", is(PRODUCT_2.intValue())))
                .andExpect(jsonPath("$.data[0].availableQuantity", is(3.0)))
                .andExpect(jsonPath("$.data[0].shortageQuantity", is(2.0)));

        assertThat(reservedOf(COMPANY_A, item1.getId())).isEqualByComparingTo("0");
        assertThat(reservedOf(COMPANY_A, item2.getId())).isEqualByComparingTo("0");
        assertThat(activeOf(5002L)).isEmpty();
    }

    @Test
    @DisplayName("Hàng ở vị trí đang đóng băng kiểm kê không được tính để giữ chỗ")
    void frozenStockIsNotReservable() throws Exception {
        stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        createAndStart(locationScope(warehouse1.getId(), List.of(binA1.getId())), COMPANY_A, manager1);

        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_A,
                () -> stockReservationService.reserveForOrder(request(5003L, warehouse1.getId(), PRODUCT_1, "1"))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(activeOf(5003L)).isEmpty();
    }

    @Test
    @DisplayName("TC-LOAD-01.6: bốn đơn xác nhận cùng lúc, mỗi đơn 3 trên tồn 10: đúng ba đơn giữ đủ, một đơn bị từ chối, tổng giữ 9 không vượt tồn")
    void concurrentConfirmationsNeverOverCommit() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < threads; i++) {
                long orderId = 6000L + i;
                executor.submit(() -> {
                    try {
                        start.await();
                        TenantContext.runAs(COMPANY_A, () -> stockReservationService.reserveForOrder(
                                request(orderId, warehouse1.getId(), PRODUCT_1, "3")));
                        reserved.incrementAndGet();
                    } catch (ApiException ex) {
                        if (ex.getErrorCode() == ErrorCode.CONFLICT) {
                            rejected.incrementAndGet();
                        } else {
                            unexpected.add(ex.toString());
                        }
                    } catch (Exception ex) {
                        unexpected.add(ex.toString());
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

        assertThat(unexpected).isEmpty();
        assertThat(reserved.get()).isEqualTo(3);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(reservedOf(COMPANY_A, item.getId())).isEqualByComparingTo("9.000");
        BigDecimal totalActive = TenantContext.callAs(COMPANY_A, () -> stockReservationRepository
                .findByProductIdAndStatus(PRODUCT_1, "ACTIVE").stream()
                .map(StockReservation::getReservedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(totalActive).isEqualByComparingTo("9.000");
        // Mỗi đơn hoặc giữ đủ 3 hoặc không giữ gì
        for (int i = 0; i < threads; i++) {
            BigDecimal ofOrder = activeOf(6000L + i).stream()
                    .map(StockReservation::getReservedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(ofOrder.compareTo(BigDecimal.ZERO) == 0 || ofOrder.compareTo(new BigDecimal("3")) == 0).isTrue();
        }
    }

    @Test
    @DisplayName("Gọi đồng thời hai lần cho cùng một đơn (gửi lại sau hết thời gian chờ): chỉ giữ một lần")
    void concurrentCallsForSameOrderReserveOnce() throws Exception {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<ReserveOrderResponse> results = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(2);
        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        results.add(TenantContext.callAs(COMPANY_A, () -> stockReservationService.reserveForOrder(
                                request(7001L, warehouse1.getId(), PRODUCT_1, "4"))));
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
        assertThat(results).extracting(ReserveOrderResponse::alreadyReserved).containsExactlyInAnyOrder(true, false);
        assertThat(reservedOf(COMPANY_A, item.getId())).isEqualByComparingTo("4.000");
    }

    @Test
    @DisplayName("Listener order.created đến sau giữ chỗ đồng bộ, sau khi đơn đã xuất hết hoặc đã hủy thì không giữ lần hai")
    void listenerDoesNotReserveAgain() {
        StockItem item = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        TenantContext.runAs(COMPANY_A, () -> stockReservationService.reserveForOrder(
                request(8001L, warehouse1.getId(), PRODUCT_1, "4")));

        OrderCreatedEvent event = new OrderCreatedEvent(8001L, "SO-R-8001", OrderType.SALES, "CONFIRMED", null, 1L,
                warehouse1.getId(), "Kho kiểm kê 1", BigDecimal.TEN, "sales", Instant.now(),
                List.of(new OrderLine(PRODUCT_1, "SKU-1", "SP 1", new BigDecimal("4.000"), "cái", BigDecimal.ONE)));
        TenantContext.runAs(COMPANY_A, () -> stockReservationService.reserveStock(event, "msg-8001"));
        assertThat(reservedOf(COMPANY_A, item.getId())).isEqualByComparingTo("4.000");
        assertThat(activeOf(8001L)).hasSize(1);

        // Đơn đã hủy (giữ chỗ RELEASED) rồi sự kiện mới tới: vẫn không giữ lại
        TenantContext.runAs(COMPANY_A, () -> stockReservationService.releaseReservationsByOrderId(8001L));
        TenantContext.runAs(COMPANY_A, () -> stockReservationService.reserveStock(event, "msg-8001-late"));
        assertThat(reservedOf(COMPANY_A, item.getId())).isEqualByComparingTo("0");
        assertThat(activeOf(8001L)).isEmpty();
    }

    @Test
    @DisplayName("Tách doanh nghiệp: doanh nghiệp B không giữ được hàng của A, giữ chỗ cùng số đơn ở B không thấy giữ chỗ của A")
    void isolatedPerCompany() throws Exception {
        StockItem itemA = stock(COMPANY_A, binA1, PRODUCT_1, null, "10.000", "0");
        stock(COMPANY_B, binOfCompanyB, PRODUCT_1, null, "5.000", "0");
        TenantContext.runAs(COMPANY_A, () -> stockReservationService.reserveForOrder(
                request(9001L, warehouse1.getId(), PRODUCT_1, "2")));

        // B gửi kho của A: không thấy dòng tồn nào của A nên thiếu hàng
        postJson(URL, request(9002L, warehouse1.getId(), PRODUCT_1, "1"), COMPANY_B, null)
                .andExpect(status().isConflict());
        // Cùng orderId 9001 ở B: không coi là đã giữ, giữ trên hàng của B
        postJson(URL, request(9001L, warehouseB.getId(), PRODUCT_1, "5"), COMPANY_B, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyReserved", is(false)));
        assertThat(reservedOf(COMPANY_A, itemA.getId())).isEqualByComparingTo("2.000");
    }
}
