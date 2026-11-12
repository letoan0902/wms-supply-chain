package vn.wms.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.client.AtpResponse;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.client.ReserveOrderRequest;
import vn.wms.order.client.StockShortageException;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.SalesOrder;
import vn.wms.order.entity.SalesOrderItem;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.SalesOrderRepository;
import vn.wms.order.service.SalesOrderService;
import vn.wms.order.support.TenantTestExecutionListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Xác nhận đơn bán đồng thời (WMS-20.4): khóa dòng đơn bán khi xác nhận nên bấm hai lần chỉ một lần thành công và chỉ
 * phát một sự kiện {@code order.created} (TC-LOAD-02.3, G3b); giữ chỗ là một phần của xác nhận nên hai đơn cùng đọc một
 * ATP thì đơn không giữ được chỗ không thành CONFIRMED (TC-LOAD-01.6). inventory-service được giả lập: phần giữ chỗ
 * dưới khóa bi quan thật được kiểm tra ở inventory-service ({@code OrderReservationInternalTest}).
 */
@SpringBootTest
@ActiveProfiles("test")
class SalesOrderConfirmConcurrencyTest {

    @Autowired
    private SalesOrderService salesOrderService;
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    @Autowired
    private CustomerRepository customerRepository;

    @MockBean
    private InventoryGateway inventoryGateway;
    @MockBean
    private WmsEventPublisher wmsEventPublisher;

    private Customer customer;
    private final List<Long> orderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setCode("KH-CONF-RACE-" + System.nanoTime());
        c.setName("Khách xác nhận đồng thời");
        c.setActive(true);
        customer = customerRepository.save(c);
        // ATP đọc trước khi giữ chỗ luôn thấy đủ (đúng tình huống hai request đọc cùng một ATP)
        when(inventoryGateway.getAtp(anyLong(), anyLong()))
                .thenReturn(new AtpResponse(1L, 1L, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("10")));
    }

    @AfterEach
    void tearDown() {
        salesOrderRepository.deleteAllById(orderIds);
        customerRepository.deleteById(customer.getId());
    }

    private SalesOrder draftOrder(String quantity) {
        SalesOrder so = new SalesOrder();
        so.setSoNumber("SO-RACE-" + System.nanoTime());
        so.setCustomer(customer);
        so.setWarehouseId(1L);
        so.setStatus(OrderStatus.DRAFT);
        so.setOrderDate(LocalDate.now());
        so.setSubtotalAmount(BigDecimal.ZERO);
        so.setTotalAmount(BigDecimal.ZERO);
        SalesOrderItem item = new SalesOrderItem();
        item.setProductId(9901L);
        item.setSkuCode("SKU-RACE");
        item.setProductName("Hàng tranh chấp");
        item.setQuantity(new BigDecimal(quantity));
        item.setDeliveredQuantity(BigDecimal.ZERO);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setLineTotal(BigDecimal.ZERO);
        so.addItem(item);
        SalesOrder saved = salesOrderRepository.save(so);
        orderIds.add(saved.getId());
        return saved;
    }

    /** Chạy các lần xác nhận cùng lúc, trả về số lần thành công; lỗi nghiệp vụ được gom vào {@code rejected}. */
    private int confirmConcurrently(List<Long> ids, List<ApiException> rejected, List<Throwable> unexpected)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(ids.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ids.size());
        AtomicInteger success = new AtomicInteger();
        try {
            for (Long id : ids) {
                executor.submit(() -> {
                    try {
                        start.await();
                        TenantContext.runAs(TenantTestExecutionListener.DEFAULT_TEST_COMPANY,
                                () -> salesOrderService.confirm(id, "sales01"));
                        success.incrementAndGet();
                    } catch (ApiException ex) {
                        rejected.add(ex);
                    } catch (Throwable ex) {
                        unexpected.add(ex);
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
        return success.get();
    }

    @Test
    @DisplayName("TC-LOAD-02.3 (G3b): bấm xác nhận hai lần cùng lúc: một lần thành công, lần sau 400, giữ chỗ một lần, một sự kiện order.created")
    void doubleConfirmSucceedsOnceAndPublishesOnce() throws Exception {
        SalesOrder so = draftOrder("4");
        when(inventoryGateway.reserveForOrder(any())).thenAnswer(invocation -> {
            Thread.sleep(200); // nới rộng khoảng tranh chấp: lần thứ hai phải chờ khóa đơn
            return null;
        });
        List<ApiException> rejected = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        int success = confirmConcurrently(List.of(so.getId(), so.getId()), rejected, unexpected);

        assertThat(unexpected).isEmpty();
        assertThat(success).isEqualTo(1);
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(salesOrderRepository.findById(so.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(inventoryGateway, times(1)).reserveForOrder(any());
        verify(wmsEventPublisher, times(1)).publishAfterCommit(eq(WmsEvents.RoutingKeys.ORDER_CREATED), any());
    }

    @Test
    @DisplayName("TC-LOAD-01.6: hai đơn cùng đọc ATP 10, mỗi đơn 8: chỉ đơn giữ được chỗ thành CONFIRMED, đơn kia 400 và vẫn DRAFT")
    void concurrentConfirmationsOfDifferentOrdersDoNotOverCommit() throws Exception {
        SalesOrder first = draftOrder("8");
        SalesOrder second = draftOrder("8");
        // inventory-service giả lập: tồn 10, giữ dưới khóa (synchronized), thiếu thì từ chối cả đơn
        BigDecimal[] remaining = {new BigDecimal("10")};
        when(inventoryGateway.reserveForOrder(any())).thenAnswer(invocation -> {
            ReserveOrderRequest request = invocation.getArgument(0);
            BigDecimal wanted = request.items().get(0).quantity();
            synchronized (remaining) {
                if (remaining[0].compareTo(wanted) < 0) {
                    throw new StockShortageException("Không đủ tồn khả dụng để giữ chỗ cho đơn " + request.orderNumber(), null);
                }
                remaining[0] = remaining[0].subtract(wanted);
            }
            return null;
        });
        List<ApiException> rejected = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        int success = confirmConcurrently(List.of(first.getId(), second.getId()), rejected, unexpected);

        assertThat(unexpected).isEmpty();
        assertThat(success).isEqualTo(1);
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).getMessage()).contains("Không thể xác nhận đơn do thiếu tồn khả dụng");
        List<OrderStatus> statuses = List.of(
                salesOrderRepository.findById(first.getId()).orElseThrow().getStatus(),
                salesOrderRepository.findById(second.getId()).orElseThrow().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.DRAFT);
        assertThat(remaining[0]).isEqualByComparingTo("2");
        verify(wmsEventPublisher, times(1)).publishAfterCommit(eq(WmsEvents.RoutingKeys.ORDER_CREATED), any());
    }
}
