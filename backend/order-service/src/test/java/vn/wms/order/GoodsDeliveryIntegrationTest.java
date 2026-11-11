package vn.wms.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.dto.CreateGdnItemRequest;
import vn.wms.order.dto.CreateGdnRequest;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.GoodsDeliveryNote;
import vn.wms.order.entity.GoodsDeliveryStatus;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.SalesOrder;
import vn.wms.order.entity.SalesOrderItem;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.GoodsDeliveryNoteRepository;
import vn.wms.order.repository.SalesOrderRepository;
import vn.wms.order.support.TenantTestExecutionListener;
import vn.wms.order.service.GoodsDeliveryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoodsDeliveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private GoodsDeliveryNoteRepository gdnRepository;

    @Autowired
    private GoodsDeliveryService goodsDeliveryService;

    @MockBean
    private WmsEventPublisher wmsEventPublisher;

    private Customer createCustomer(String code, String name) {
        Customer c = new Customer();
        c.setCode(code);
        c.setName(name);
        c.setTaxCode("TAX-" + code);
        c.setActive(true);
        return customerRepository.save(c);
    }

    private SalesOrder createConfirmedSo(Customer customer, Long warehouseId, BigDecimal qty, BigDecimal price) {
        SalesOrder so = new SalesOrder();
        so.setSoNumber("SO-TEST-" + System.nanoTime());
        so.setCustomer(customer);
        so.setWarehouseId(warehouseId);
        so.setWarehouseName("Kho Tân Bình");
        so.setStatus(OrderStatus.CONFIRMED);
        so.setOrderDate(LocalDate.now());
        so.setSubtotalAmount(qty.multiply(price));
        so.setTotalAmount(qty.multiply(price));

        SalesOrderItem item = new SalesOrderItem();
        item.setProductId(10L);
        item.setSkuCode("SKU-MILK-001");
        item.setProductName("Sữa tươi tiệt trùng 1L");
        item.setUom("Hộp");
        item.setQuantity(qty);
        item.setDeliveredQuantity(BigDecimal.ZERO);
        item.setUnitPrice(price);
        item.setLineTotal(qty.multiply(price));
        item.setOnHandQuantity(new BigDecimal("100"));
        item.setAtpQuantity(new BigDecimal("100"));

        so.addItem(item);
        return salesOrderRepository.save(so);
    }

    @Test
    @DisplayName("Lập phiếu xuất GDN xuất đủ 100% hàng -> SO tự động chuyển sang COMPLETED")
    void createGdnFullDeliveryCompletesSo() throws Exception {
        Customer customer = createCustomer("KH-GDN-01", "Công ty Alpha");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("20"), new BigDecimal("25000"));
        SalesOrderItem soItem = so.getItems().get(0);

        CreateGdnRequest req = new CreateGdnRequest(
                so.getId(),
                LocalDate.now(),
                GoodsDeliveryStatus.COMPLETED,
                "Anh Tuấn",
                "0912345678",
                "123 Nguyễn Huệ, Q1",
                "Xuất kho toàn bộ đơn hàng",
                List.of(new CreateGdnItemRequest(
                        soItem.getId(),
                        soItem.getProductId(),
                        soItem.getSkuCode(),
                        soItem.getProductName(),
                        soItem.getUom(),
                        new BigDecimal("20"),
                        soItem.getUnitPrice(),
                        "LOT-202611-01",
                        5L,
                        "A-01-01",
                        "Đã nhặt đủ 20 hộp"
                ))
        );

        mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Name", "thukho_viet")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.gdnNumber", startsWith("GDN-")))
                .andExpect(jsonPath("$.data.soNumber", is(so.getSoNumber())))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.dispatchedBy", is("thukho_viet")))
                .andExpect(jsonPath("$.data.totalQuantity").value(20))
                .andExpect(jsonPath("$.data.totalAmount").value(500000.00))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].deliveredQuantity").value(20));

        // Kiểm tra SO đã hoàn tất
        SalesOrder reloadedSo = salesOrderRepository.findById(so.getId()).orElseThrow();
        assertThat(reloadedSo.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(reloadedSo.getItems().get(0).getDeliveredQuantity()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Lập phiếu xuất kho từng đợt (Partial Delivery): Đợt 1 xuất 10, Đợt 2 xuất 10 -> hoàn tất đơn")
    void createGdnPartialDeliveryWorkflow() throws Exception {
        Customer customer = createCustomer("KH-GDN-02", "Công ty Beta");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("20"), new BigDecimal("30000"));
        SalesOrderItem soItem = so.getItems().get(0);

        // Đợt 1: Xuất 8 cái
        CreateGdnRequest req1 = new CreateGdnRequest(
                so.getId(),
                LocalDate.now(),
                GoodsDeliveryStatus.COMPLETED,
                "Người nhận đợt 1",
                "0911111111",
                "Kho đích",
                "Xuất đợt 1",
                List.of(new CreateGdnItemRequest(
                        soItem.getId(), soItem.getProductId(), soItem.getSkuCode(), soItem.getProductName(),
                        soItem.getUom(), new BigDecimal("8"), soItem.getUnitPrice(),
                        "LOT-01", 1L, "A-01-01", "Đợt 1"
                ))
        );

        mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalQuantity").value(8));

        SalesOrder soAfterRound1 = salesOrderRepository.findById(so.getId()).orElseThrow();
        assertThat(soAfterRound1.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(soAfterRound1.getItems().get(0).getDeliveredQuantity()).isEqualByComparingTo("8");

        // Đợt 2: Xuất nốt 12 cái
        CreateGdnRequest req2 = new CreateGdnRequest(
                so.getId(),
                LocalDate.now(),
                GoodsDeliveryStatus.COMPLETED,
                "Người nhận đợt 2",
                "0911111111",
                "Kho đích",
                "Xuất nốt đợt 2",
                List.of(new CreateGdnItemRequest(
                        soItem.getId(), soItem.getProductId(), soItem.getSkuCode(), soItem.getProductName(),
                        soItem.getUom(), new BigDecimal("12"), soItem.getUnitPrice(),
                        "LOT-02", 2L, "A-01-02", "Đợt 2"
                ))
        );

        mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalQuantity").value(12));

        SalesOrder soAfterRound2 = salesOrderRepository.findById(so.getId()).orElseThrow();
        assertThat(soAfterRound2.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(soAfterRound2.getItems().get(0).getDeliveredQuantity()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Từ chối lập GDN khi số lượng xuất vượt quá số lượng còn lại của đơn hàng")
    void createGdnExceedingOrderedQuantityThrowsBadRequest() throws Exception {
        Customer customer = createCustomer("KH-GDN-03", "Công ty Gamma");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("10"), new BigDecimal("15000"));
        SalesOrderItem soItem = so.getItems().get(0);

        CreateGdnRequest req = new CreateGdnRequest(
                so.getId(),
                LocalDate.now(),
                GoodsDeliveryStatus.COMPLETED,
                "Người nhận",
                "0911111111",
                "Địa chỉ",
                "Xuất quá số lượng",
                List.of(new CreateGdnItemRequest(
                        soItem.getId(), soItem.getProductId(), soItem.getSkuCode(), soItem.getProductName(),
                        soItem.getUom(), new BigDecimal("15"), // Vượt quá 10
                        soItem.getUnitPrice(), "LOT-01", 1L, "A-01-01", null
                ))
        );

        mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("Từ chối lập GDN khi đơn hàng SO đang ở trạng thái DRAFT")
    void createGdnWhenSoIsDraftThrowsBadRequest() throws Exception {
        Customer customer = createCustomer("KH-GDN-04", "Công ty Delta");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("10"), new BigDecimal("15000"));
        so.setStatus(OrderStatus.DRAFT);
        so = salesOrderRepository.save(so);
        SalesOrderItem soItem = so.getItems().get(0);

        CreateGdnRequest req = new CreateGdnRequest(
                so.getId(),
                LocalDate.now(),
                GoodsDeliveryStatus.COMPLETED,
                "Người nhận", "0911111111", "Địa chỉ", null,
                List.of(new CreateGdnItemRequest(
                        soItem.getId(), soItem.getProductId(), soItem.getSkuCode(), soItem.getProductName(),
                        soItem.getUom(), new BigDecimal("5"), soItem.getUnitPrice(), null, null, null, null
                ))
        );

        mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("Xác nhận xuất xưởng (Dispatch) hoàn tất xuất kho cho phiếu đang ở trạng thái PICKING")
    void dispatchGdnWorkflow() throws Exception {
        Customer customer = createCustomer("KH-GDN-05", "Công ty Epsilon");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("10"), new BigDecimal("20000"));
        SalesOrderItem soItem = so.getItems().get(0);

        // Tạo phiếu xuất ở trạng thái PICKING
        CreateGdnRequest req = new CreateGdnRequest(
                so.getId(),
                LocalDate.now(),
                GoodsDeliveryStatus.PICKING,
                "Người nhận", "0911111111", "Địa chỉ", null,
                List.of(new CreateGdnItemRequest(
                        soItem.getId(), soItem.getProductId(), soItem.getSkuCode(), soItem.getProductName(),
                        soItem.getUom(), new BigDecimal("10"), soItem.getUnitPrice(), null, null, null, null
                ))
        );

        String responseJson = mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PICKING")))
                .andReturn().getResponse().getContentAsString();

        Long gdnId = objectMapper.readTree(responseJson).path("data").path("id").asLong();

        // Xác nhận dispatch xuất kho
        mockMvc.perform(post("/api/orders/goods-delivery-notes/" + gdnId + "/dispatch")
                        .header("X-User-Name", "nhanvien_kho")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.dispatchedBy", is("nhanvien_kho")));
    }

    @Test
    @DisplayName("Tra cứu danh sách và thẻ thống kê GDN (/summary)")
    void listAndSummaryGdns() throws Exception {
        Customer customer = createCustomer("KH-GDN-06", "Công ty Zeta");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("5"), new BigDecimal("10000"));
        SalesOrderItem soItem = so.getItems().get(0);

        CreateGdnRequest req = new CreateGdnRequest(
                so.getId(), LocalDate.now(), GoodsDeliveryStatus.COMPLETED,
                "A", "09", "DC", null,
                List.of(new CreateGdnItemRequest(
                        soItem.getId(), soItem.getProductId(), soItem.getSkuCode(), soItem.getProductName(),
                        soItem.getUom(), new BigDecimal("5"), soItem.getUnitPrice(), null, null, null, null
                ))
        );

        mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders/goods-delivery-notes/summary")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalGdns").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Từ chối lập GDN ở trạng thái CANCELLED: không được cộng số lượng đã xuất khi hàng không rời kho")
    void createGdnWithCancelledStatusThrowsBadRequest() throws Exception {
        Customer customer = createCustomer("KH-GDN-07", "Công ty Eta");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("10"), new BigDecimal("10000"));
        SalesOrderItem soItem = so.getItems().get(0);

        CreateGdnRequest req = new CreateGdnRequest(
                so.getId(), LocalDate.now(), GoodsDeliveryStatus.CANCELLED,
                "A", "09", "DC", null,
                List.of(gdnItem(soItem, "10"))
        );

        mockMvc.perform(post("/api/orders/goods-delivery-notes")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        SalesOrder reloaded = salesOrderRepository.findById(so.getId()).orElseThrow();
        assertThat(reloaded.getItems().get(0).getDeliveredQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Hai phiếu xuất đồng thời cho cùng một đơn (bấm hai lần): chỉ một phiếu được lập, không xuất vượt số lượng đặt")
    void concurrentGdnForSameSoDoesNotOverDeliver() throws Exception {
        Customer customer = createCustomer("KH-GDN-RACE", "Công ty Đồng thời");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("10"), new BigDecimal("10000"));
        SalesOrderItem soItem = so.getItems().get(0);
        CreateGdnRequest req = new CreateGdnRequest(
                so.getId(), LocalDate.now(), GoodsDeliveryStatus.COMPLETED,
                "A", "09", "DC", null, List.of(gdnItem(soItem, "10")));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        // Luồng mới không mang TenantContext: chạy dưới doanh nghiệp của test (docs/MULTI_TENANT.md mục 3.7)
                        TenantContext.runAs(TenantTestExecutionListener.DEFAULT_TEST_COMPANY,
                                () -> goodsDeliveryService.create(req, "thukho"));
                        success.incrementAndGet();
                    } catch (ApiException ex) {
                        rejected.incrementAndGet();
                    } catch (Exception ex) {
                        rejected.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();

            assertThat(success.get()).isEqualTo(1);
            assertThat(rejected.get()).isEqualTo(1);
            assertThat(gdnRepository.findBySalesOrderIdOrderByIdDesc(so.getId())).hasSize(1);
            SalesOrder reloaded = salesOrderRepository.findById(so.getId()).orElseThrow();
            assertThat(reloaded.getItems().get(0).getDeliveredQuantity()).isEqualByComparingTo("10");
            assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            // Chỉ đúng một sự kiện order.dispatched, tức inventory-service chỉ trừ tồn một lần
            verify(wmsEventPublisher, times(1))
                    .publishAfterCommit(eq(WmsEvents.RoutingKeys.ORDER_DISPATCHED), any());
        } finally {
            executor.shutdownNow();
            cleanUp(so, customer);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Xác nhận xuất kho hai lần đồng thời cho cùng một phiếu: chỉ phát một sự kiện trừ tồn")
    void concurrentDispatchPublishesOnce() throws Exception {
        Customer customer = createCustomer("KH-GDN-RACE2", "Công ty Đồng thời 2");
        SalesOrder so = createConfirmedSo(customer, 1L, new BigDecimal("10"), new BigDecimal("10000"));
        SalesOrderItem soItem = so.getItems().get(0);
        Long gdnId = goodsDeliveryService.create(new CreateGdnRequest(
                so.getId(), LocalDate.now(), GoodsDeliveryStatus.PICKING,
                "A", "09", "DC", null, List.of(gdnItem(soItem, "10"))), "thukho").id();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        TenantContext.runAs(TenantTestExecutionListener.DEFAULT_TEST_COMPANY,
                                () -> goodsDeliveryService.dispatch(gdnId, "thukho"));
                    } catch (Exception ignored) {
                        // Kết quả kiểm tra qua số lần phát sự kiện bên dưới
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();

            assertThat(gdnRepository.findById(gdnId).orElseThrow().getStatus()).isEqualTo(GoodsDeliveryStatus.COMPLETED);
            verify(wmsEventPublisher, times(1))
                    .publishAfterCommit(eq(WmsEvents.RoutingKeys.ORDER_DISPATCHED), any());
        } finally {
            executor.shutdownNow();
            cleanUp(so, customer);
        }
    }

    private CreateGdnItemRequest gdnItem(SalesOrderItem soItem, String quantity) {
        return new CreateGdnItemRequest(
                soItem.getId(), soItem.getProductId(), soItem.getSkuCode(), soItem.getProductName(),
                soItem.getUom(), new BigDecimal(quantity), soItem.getUnitPrice(), null, null, null, null);
    }

    private void cleanUp(SalesOrder so, Customer customer) {
        gdnRepository.deleteAll(gdnRepository.findBySalesOrderIdOrderByIdDesc(so.getId()));
        salesOrderRepository.deleteById(so.getId());
        customerRepository.deleteById(customer.getId());
    }
}
