package vn.wms.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.client.AtpResponse;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.client.WarehouseSummary;
import vn.wms.order.dto.CreateCustomerRequest;
import vn.wms.order.dto.CreatePurchaseOrderRequest;
import vn.wms.order.dto.CreateSalesOrderRequest;
import vn.wms.order.dto.CreateSupplierRequest;
import vn.wms.order.dto.PurchaseOrderItemRequest;
import vn.wms.order.dto.PurchaseOrderResponse;
import vn.wms.order.dto.SalesOrderItemRequest;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.DocumentSequenceRepository;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SalesOrderRepository;
import vn.wms.order.repository.SupplierRepository;
import vn.wms.order.service.CustomerService;
import vn.wms.order.service.PurchaseOrderService;
import vn.wms.order.service.SalesOrderService;
import vn.wms.order.service.SupplierService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.wms.common.test.GatewayHeaders.asUser;

/**
 * Biểu đồ mua bán theo thời gian trên dashboard (WMS-16.4): chứng từ được tính, gom kỳ, che số tiền theo vai trò,
 * phạm vi kho và cách ly doanh nghiệp. Dữ liệu tạo qua đúng các service nghiệp vụ; lớp test không đặt
 * {@code @Transactional} để mỗi lời gọi đọc đúng doanh nghiệp của nó như request thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseSalesTrendIntegrationTest {

    private static final String URL = "/api/orders/reports/purchase-sales-trend";
    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final Long WAREHOUSE = 11L;
    private static final Long OTHER_WAREHOUSE = 12L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private SupplierRepository supplierRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SalesOrderRepository salesOrderRepository;
    @Autowired private DocumentSequenceRepository documentSequenceRepository;

    @Autowired private SupplierService supplierService;
    @Autowired private CustomerService customerService;
    @Autowired private PurchaseOrderService purchaseOrderService;
    @Autowired private SalesOrderService salesOrderService;

    @MockBean private InventoryGateway inventoryGateway;
    @MockBean private WmsEventPublisher wmsEventPublisher;

    @BeforeEach
    void setUp() {
        when(inventoryGateway.requireActiveWarehouse(anyLong()))
                .thenAnswer(inv -> new WarehouseSummary(inv.getArgument(0), "WH", "Kho", "Hà Nội", true));
        when(inventoryGateway.getAtp(any(), any())).thenAnswer(inv -> new AtpResponse(
                inv.getArgument(0), inv.getArgument(1), new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1000")));
        when(wmsEventPublisher.publish(anyString(), any())).thenReturn("message-id");
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (Long company : List.of(COMPANY_A, COMPANY_B)) {
            TenantContext.runAs(company, () -> {
                salesOrderRepository.deleteAll();
                purchaseOrderRepository.deleteAll();
                supplierRepository.deleteAll();
                customerRepository.deleteAll();
                documentSequenceRepository.deleteAll();
            });
        }
    }

    /**
     * Tạo hôm nay cho doanh nghiệp hiện tại: một đơn mua đã duyệt (qty x 1.000), một đơn mua chờ duyệt (không tính),
     * một đơn bán đã xác nhận (qty x 2.000), một đơn bán nháp (không tính), tất cả ở kho {@link #WAREHOUSE}.
     */
    private void createDocuments(String quantity) {
        Long supplierId = supplierService.create(new CreateSupplierRequest("NCC-01", "Nhà cung cấp", null, null, null,
                null, null, null, null, null, null, null)).id();
        Long customerId = customerService.create(new CreateCustomerRequest("KH-01", "Khách hàng", CustomerType.RETAIL,
                null, null, null, null, null, null, null, null, null)).id();
        LocalDate today = LocalDate.now();

        PurchaseOrderResponse approved = purchaseOrderService.create(purchaseOrder(supplierId, today, quantity));
        purchaseOrderService.approve(approved.id(), "truongkho");
        purchaseOrderService.create(purchaseOrder(supplierId, today, "999"));

        salesOrderService.create(salesOrder(customerId, OrderStatus.CONFIRMED, quantity), "banhang");
        salesOrderService.create(salesOrder(customerId, OrderStatus.DRAFT, "777"), "banhang");
    }

    private static CreatePurchaseOrderRequest purchaseOrder(Long supplierId, LocalDate date, String quantity) {
        return new CreatePurchaseOrderRequest(null, supplierId, WAREHOUSE, "Kho tổng", date, null, null,
                OrderStatus.PENDING, List.of(new PurchaseOrderItemRequest(100L, "SKU-01", "Hàng mẫu", "Hộp",
                new BigDecimal(quantity), new BigDecimal("1000"), null, null, null, null, null)));
    }

    private static CreateSalesOrderRequest salesOrder(Long customerId, OrderStatus status, String quantity) {
        return new CreateSalesOrderRequest(null, customerId, WAREHOUSE, null, "Địa chỉ giao", "Người nhận",
                "0900000000", null, null, null, status, false,
                List.of(new SalesOrderItemRequest(100L, "SKU-01", "Hàng mẫu", "Hộp",
                        new BigDecimal(quantity), new BigDecimal("2000"), null, null, null, null, null)));
    }

    private MockHttpServletRequestBuilder as(String role, Long company, Long... warehouseIds) {
        return get(URL).with(asUser("user-" + role.toLowerCase(), company, List.of(role), List.of(warehouseIds)));
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    @Test
    @DisplayName("Chỉ tính đơn mua đã duyệt và đơn bán đã xác nhận; kỳ theo ngày, đủ 7 ngày, tổng tiền bán khớp thẻ doanh số")
    void countsApprovedPurchasesAndConfirmedSalesByDay() throws Exception {
        TenantContext.runAs(COMPANY_A, () -> createDocuments("10"));

        JsonNode week = data(as("ADMIN", COMPANY_A).param("dateRange", "7_DAYS"));
        assertThat(week.path("bucket").asText()).isEqualTo("DAY");
        assertThat(week.path("amountVisible").asBoolean()).isTrue();
        assertThat(week.path("fromDate").asText()).isEqualTo(LocalDate.now().minusDays(6).toString());
        assertThat(week.path("toDate").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(week.path("points")).hasSize(7);
        JsonNode todayPoint = week.path("points").get(6);
        assertThat(todayPoint.path("label").asText())
                .isEqualTo(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM")));
        assertThat(todayPoint.path("figures").path("purchaseOrderCount").asLong()).isEqualTo(1);
        assertThat(todayPoint.path("figures").path("salesOrderCount").asLong()).isEqualTo(1);
        // Các ngày trước không phát sinh vẫn có điểm với giá trị 0
        assertThat(week.path("points").get(0).path("figures").path("salesOrderCount").asLong()).isZero();
        assertThat(week.path("points").get(0).path("figures").path("salesAmount").decimalValue())
                .isEqualByComparingTo("0");

        JsonNode totals = week.path("totals");
        assertThat(totals.path("purchaseOrderCount").asLong()).isEqualTo(1);
        assertThat(totals.path("purchaseQuantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(totals.path("purchaseAmount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(totals.path("salesOrderCount").asLong()).isEqualTo(1);
        assertThat(totals.path("salesQuantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(totals.path("salesAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(week.path("amountNote").isNull()).isTrue();

        // "Tháng này": tổng tiền bán bằng doanh số tháng của thẻ KPI (cùng trạng thái, cùng ngày đặt hàng)
        JsonNode month = data(as("ACCOUNTANT", COMPANY_A).param("dateRange", "THIS_MONTH"));
        assertThat(month.path("points")).hasSize(LocalDate.now().getDayOfMonth());
        String kpiBody = mockMvc.perform(get("/internal/orders/kpi-metrics").header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        BigDecimal kpiRevenue = objectMapper.readTree(kpiBody).path("data").path("currentMonthRevenue").decimalValue();
        assertThat(month.path("totals").path("salesAmount").decimalValue()).isEqualByComparingTo(kpiRevenue);
    }

    @Test
    @DisplayName("Quý này gom theo tháng, nhãn MM/yyyy, kỳ cuối kết thúc hôm nay")
    void quarterIsBucketedByMonth() throws Exception {
        TenantContext.runAs(COMPANY_A, () -> createDocuments("10"));
        LocalDate today = LocalDate.now();
        int monthsInQuarterSoFar = (today.getMonthValue() - 1) % 3 + 1;

        JsonNode quarter = data(as("ADMIN", COMPANY_A).param("dateRange", "THIS_QUARTER"));
        assertThat(quarter.path("bucket").asText()).isEqualTo("MONTH");
        JsonNode points = quarter.path("points");
        assertThat(points).hasSize(monthsInQuarterSoFar);
        JsonNode last = points.get(monthsInQuarterSoFar - 1);
        assertThat(last.path("label").asText()).isEqualTo(today.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        assertThat(last.path("periodStart").asText()).isEqualTo(today.withDayOfMonth(1).toString());
        assertThat(last.path("periodEnd").asText()).isEqualTo(today.toString());
        assertThat(last.path("figures").path("salesOrderCount").asLong()).isEqualTo(1);
        assertThat(quarter.path("totals").path("purchaseAmount").decimalValue()).isEqualByComparingTo("10000");

        // Toàn bộ thời gian cũng gom theo tháng, bắt đầu từ tháng có đơn sớm nhất
        JsonNode allTime = data(as("ADMIN", COMPANY_A).param("dateRange", "all_time"));
        assertThat(allTime.path("dateRange").asText()).isEqualTo("ALL_TIME");
        assertThat(allTime.path("bucket").asText()).isEqualTo("MONTH");
        assertThat(allTime.path("points")).hasSize(1);
        assertThat(allTime.path("totals").path("salesOrderCount").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("Doanh nghiệp B không thấy số liệu của doanh nghiệp A và ngược lại")
    void companiesAreIsolated() throws Exception {
        TenantContext.runAs(COMPANY_A, () -> createDocuments("10"));
        TenantContext.runAs(COMPANY_B, () -> createDocuments("3"));

        JsonNode totalsA = data(as("ADMIN", COMPANY_A).param("dateRange", "7_DAYS")).path("totals");
        JsonNode totalsB = data(as("ADMIN", COMPANY_B).param("dateRange", "7_DAYS")).path("totals");

        assertThat(totalsA.path("purchaseOrderCount").asLong()).isEqualTo(1);
        assertThat(totalsA.path("purchaseAmount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(totalsA.path("salesAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(totalsB.path("purchaseOrderCount").asLong()).isEqualTo(1);
        assertThat(totalsB.path("purchaseAmount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(totalsB.path("salesQuantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(totalsB.path("salesAmount").decimalValue()).isEqualByComparingTo("6000");

        // Toàn bộ thời gian của B cũng không lấy ngày đơn của A
        cleanUpOnly(COMPANY_B);
        JsonNode emptyB = data(as("ADMIN", COMPANY_B).param("dateRange", "ALL_TIME"));
        assertThat(emptyB.path("points")).hasSize(1);
        assertThat(emptyB.path("totals").path("salesOrderCount").asLong()).isZero();
        assertThat(emptyB.path("totals").path("purchaseAmount").decimalValue()).isEqualByComparingTo("0");
    }

    private void cleanUpOnly(Long company) {
        TenantContext.runAs(company, () -> {
            salesOrderRepository.deleteAll();
            purchaseOrderRepository.deleteAll();
        });
    }

    @Test
    @DisplayName("Vai trò không xem tài chính nhận số đơn, số lượng nhưng không nhận số tiền; kho lọc theo phạm vi")
    void amountsHiddenAndWarehouseScopeApplied() throws Exception {
        TenantContext.runAs(COMPANY_A, () -> createDocuments("10"));

        for (String role : List.of("WAREHOUSE_MANAGER", "WAREHOUSE_STAFF", "PURCHASING", "SALES")) {
            JsonNode report = data(as(role, COMPANY_A, WAREHOUSE).param("dateRange", "7_DAYS"));
            assertThat(report.path("amountVisible").asBoolean()).as(role).isFalse();
            assertThat(report.path("amountNote").asText()).as(role).isNotBlank();
            assertThat(report.path("totals").path("purchaseAmount").isNull()).as(role).isTrue();
            assertThat(report.path("totals").path("salesAmount").isNull()).as(role).isTrue();
            assertThat(report.path("totals").path("salesOrderCount").asLong()).as(role).isEqualTo(1);
            assertThat(report.path("totals").path("purchaseQuantity").decimalValue()).as(role)
                    .isEqualByComparingTo("10");
            for (JsonNode point : report.path("points")) {
                assertThat(point.path("figures").path("salesAmount").isNull()).as(role).isTrue();
                assertThat(point.path("figures").path("purchaseAmount").isNull()).as(role).isTrue();
            }
            assertThat(report.path("meta").path("warehouseId").asLong()).isEqualTo(WAREHOUSE);
        }

        // Nhân viên kho khác: không thấy đơn của kho 11
        JsonNode otherWarehouse = data(as("WAREHOUSE_STAFF", COMPANY_A, OTHER_WAREHOUSE).param("dateRange", "7_DAYS"));
        assertThat(otherWarehouse.path("totals").path("salesOrderCount").asLong()).isZero();
        assertThat(otherWarehouse.path("totals").path("purchaseOrderCount").asLong()).isZero();

        // Xin kho ngoài phạm vi, hoặc chưa được gán kho nào: 403
        mockMvc.perform(as("WAREHOUSE_STAFF", COMPANY_A, OTHER_WAREHOUSE).param("warehouseId", String.valueOf(WAREHOUSE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(as("SALES", COMPANY_A))
                .andExpect(status().isForbidden());

        // ADMIN lọc đúng kho 12: không có đơn
        JsonNode adminOther = data(as("ADMIN", COMPANY_A).param("warehouseId", String.valueOf(OTHER_WAREHOUSE)));
        assertThat(adminOther.path("totals").path("salesOrderCount").asLong()).isZero();
        assertThat(adminOther.path("totals").path("salesAmount").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Khoảng thời gian không hợp lệ trả 400")
    void invalidDateRangeIsRejected() throws Exception {
        mockMvc.perform(as("ADMIN", COMPANY_A).param("dateRange", "LAST_YEAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
