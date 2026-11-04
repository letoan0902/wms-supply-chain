package vn.wms.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.order.client.AtpResponse;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.client.WarehouseSummary;
import vn.wms.order.dto.AtpCheckRequest;
import vn.wms.order.dto.CreateSalesOrderRequest;
import vn.wms.order.dto.SalesOrderItemRequest;
import vn.wms.order.dto.UpdateSalesOrderRequest;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.SalesOrder;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.SalesOrderRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesOrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @MockBean
    private InventoryGateway inventoryGateway;

    private Customer sampleCustomer;

    @BeforeEach
    void setUp() {
        // Mock warehouse validation
        when(inventoryGateway.requireActiveWarehouse(1L))
                .thenReturn(new WarehouseSummary(1L, "WH-HN", "Kho Tổng Hà Nội", "Hà Nội", true));

        // Default ATP mock: sufficient stock for all products
        when(inventoryGateway.getAtp(anyLong(), anyLong()))
                .thenReturn(new AtpResponse(1L, 1L, new BigDecimal("100.000"), new BigDecimal("10.000"), new BigDecimal("90.000")));

        sampleCustomer = new Customer();
        sampleCustomer.setCode("KH-SO-001");
        sampleCustomer.setName("Công ty Cổ phần Thương mại Minh Phát");
        sampleCustomer.setCustomerType(CustomerType.WHOLESALE);
        sampleCustomer.setCreditLimit(new BigDecimal("100000000.00")); // Hạn mức 100 triệu
        sampleCustomer.setCurrentDebt(BigDecimal.ZERO);
        sampleCustomer.setActive(true);
        sampleCustomer = customerRepository.save(sampleCustomer);
    }

    @Test
    @DisplayName("Tạo đơn bán hàng thành công khi ATP đủ: tự sinh mã SO, tính toán tiền khớp và trạng thái CONFIRMED")
    void createSalesOrderSuccessfullyWithSufficientAtpAndCalculations() throws Exception {
        // Dòng 1: qty = 10, unitPrice = 100,000 -> raw = 1,000,000
        // Chiết khấu 10% = 100,000 -> net = 900,000
        // VAT 8% = 72,000 -> lineTotal = 972,000
        SalesOrderItemRequest item1 = new SalesOrderItemRequest(
                101L, "SKU-PROD-01", "Bánh Cosy quy bơ", "Hộp",
                new BigDecimal("10.000"), new BigDecimal("100000.00"),
                new BigDecimal("10.00"), null,
                new BigDecimal("8.00"), null,
                "Hàng mới nhập"
        );

        // Dòng 2: qty = 5, unitPrice = 200,000 -> raw = 1,000,000
        // Không chiết khấu (0%) -> net = 1,000,000
        // VAT 10% = 100,000 -> lineTotal = 1,100,000
        SalesOrderItemRequest item2 = new SalesOrderItemRequest(
                102L, "SKU-PROD-02", "Kẹo Socola KitKat", "Gói",
                new BigDecimal("5.000"), new BigDecimal("200000.00"),
                BigDecimal.ZERO, null,
                new BigDecimal("10.00"), null,
                null
        );

        CreateSalesOrderRequest request = new CreateSalesOrderRequest(
                null, // Tự sinh SO-YYYYMMDD-XXXX
                sampleCustomer.getId(),
                1L,
                "Kho Tổng Hà Nội",
                "Số 88 Cầu Giấy, Hà Nội",
                "Anh Tuấn",
                "0912345678",
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                "Giao nhanh trong buổi sáng",
                OrderStatus.CONFIRMED,
                true,
                List.of(item1, item2)
        );

        // Grand Total = 972,000 + 1,100,000 = 2,072,000
        mockMvc.perform(post("/api/orders/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.soNumber", startsWith("SO-")))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.subtotalAmount", is(2000000.0)))
                .andExpect(jsonPath("$.data.discountAmount", is(100000.0)))
                .andExpect(jsonPath("$.data.taxAmount", is(172000.0)))
                .andExpect(jsonPath("$.data.totalAmount", is(2072000.0)))
                .andExpect(jsonPath("$.data.items.length()", is(2)))
                .andExpect(jsonPath("$.data.items[0].lineTotal", is(972000.0)))
                .andExpect(jsonPath("$.data.items[0].isBackorder", is(false)))
                .andExpect(jsonPath("$.data.items[1].lineTotal", is(1100000.0)))
                .andExpect(jsonPath("$.data.items[1].isBackorder", is(false)));
    }

    @Test
    @DisplayName("Tạo đơn bán hàng khi ATP thiếu và allowBackorder = true -> Chuyển sang BACKORDER kèm lý do")
    void createSalesOrderWhenAtpInsufficientAndAllowBackorderTrue() throws Exception {
        // Mock ATP thiếu hàng: SKU cần 50 nhưng tồn ATP chỉ còn 20
        when(inventoryGateway.getAtp(201L, 1L))
                .thenReturn(new AtpResponse(201L, 1L, new BigDecimal("30.000"), new BigDecimal("10.000"), new BigDecimal("20.000")));

        SalesOrderItemRequest item = new SalesOrderItemRequest(
                201L, "SKU-SHORTAGE", "Sản phẩm thiếu tồn", "Cái",
                new BigDecimal("50.000"), new BigDecimal("100000.00"),
                BigDecimal.ZERO, null,
                new BigDecimal("10.00"), null,
                null
        );

        CreateSalesOrderRequest request = new CreateSalesOrderRequest(
                null,
                sampleCustomer.getId(),
                1L,
                "Kho Tổng Hà Nội",
                "Hà Nội",
                "Người nhận",
                "0987654321",
                LocalDate.now(),
                null,
                "Đơn chờ bù",
                OrderStatus.CONFIRMED,
                true, // Cho phép chuyển BACKORDER
                List.of(item)
        );

        mockMvc.perform(post("/api/orders/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("BACKORDER")))
                .andExpect(jsonPath("$.data.backorderReason", containsString("SKU SKU-SHORTAGE thiếu 30")))
                .andExpect(jsonPath("$.data.items[0].isBackorder", is(true)))
                .andExpect(jsonPath("$.data.items[0].atpQuantity", is(20.0)));
    }

    @Test
    @DisplayName("Tạo đơn bán hàng khi ATP thiếu và allowBackorder = false -> Trả về 400 Bad Request")
    void createSalesOrderWhenAtpInsufficientAndAllowBackorderFalse() throws Exception {
        when(inventoryGateway.getAtp(202L, 1L))
                .thenReturn(new AtpResponse(202L, 1L, new BigDecimal("10.000"), new BigDecimal("5.000"), new BigDecimal("5.000")));

        SalesOrderItemRequest item = new SalesOrderItemRequest(
                202L, "SKU-STRICT", "Sản phẩm không cho backorder", "Cái",
                new BigDecimal("20.000"), new BigDecimal("50000.00"),
                BigDecimal.ZERO, null,
                BigDecimal.ZERO, null,
                null
        );

        CreateSalesOrderRequest request = new CreateSalesOrderRequest(
                null,
                sampleCustomer.getId(),
                1L,
                "Kho Tổng Hà Nội",
                "Hà Nội",
                "Người nhận",
                "0987654321",
                LocalDate.now(),
                null,
                "Không cho phép thiếu",
                OrderStatus.CONFIRMED,
                false, // KHÔNG cho phép chuyển BACKORDER -> Báo lỗi
                List.of(item)
        );

        mockMvc.perform(post("/api/orders/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("không đủ tồn khả dụng")));
    }

    @Test
    @DisplayName("Tạo đơn bán hàng bị chặn khi vượt hạn mức công nợ khách hàng (FR14)")
    void createSalesOrderBlockedWhenCustomerCreditLimitExceeded() throws Exception {
        // Hạn mức nợ 10 triệu, nợ hiện tại 8 triệu -> chỉ còn được mua thêm 2 triệu
        sampleCustomer.setCreditLimit(new BigDecimal("10000000.00"));
        sampleCustomer.setCurrentDebt(new BigDecimal("8000000.00"));
        customerRepository.save(sampleCustomer);

        // Đơn hàng trị giá 5 triệu -> Tổng nợ dự kiến 13 triệu > 10 triệu -> BỊ CHẶN
        SalesOrderItemRequest item = new SalesOrderItemRequest(
                301L, "SKU-EXPENSIVE", "Sản phẩm giá cao", "Thùng",
                new BigDecimal("5.000"), new BigDecimal("1000000.00"),
                BigDecimal.ZERO, null,
                BigDecimal.ZERO, null,
                null
        );

        CreateSalesOrderRequest request = new CreateSalesOrderRequest(
                null,
                sampleCustomer.getId(),
                1L,
                "Kho Tổng Hà Nội",
                "Hà Nội",
                "Khách nợ",
                "0912345678",
                LocalDate.now(),
                null,
                "Đơn vượt hạn mức",
                OrderStatus.CONFIRMED,
                true,
                List.of(item)
        );

        mockMvc.perform(post("/api/orders/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("vượt quá hạn mức nợ")));
    }

    @Test
    @DisplayName("Kiểm tra API preview tồn khả dụng ATP: /api/orders/sales/check-atp")
    void testCheckAtpEndpoint() throws Exception {
        when(inventoryGateway.getAtp(401L, 1L))
                .thenReturn(new AtpResponse(401L, 1L, new BigDecimal("50.000"), new BigDecimal("10.000"), new BigDecimal("40.000")));
        when(inventoryGateway.getAtp(402L, 1L))
                .thenReturn(new AtpResponse(402L, 1L, new BigDecimal("15.000"), new BigDecimal("10.000"), new BigDecimal("5.000")));

        AtpCheckRequest request = new AtpCheckRequest(
                1L,
                List.of(
                        new AtpCheckRequest.ItemCheck(401L, "SKU-OK", "Bánh ngọt", new BigDecimal("30.000")), // Đủ (30 <= 40)
                        new AtpCheckRequest.ItemCheck(402L, "SKU-LACK", "Kẹo ngọt", new BigDecimal("10.000")) // Thiếu (10 > 5)
                )
        );

        mockMvc.perform(post("/api/orders/sales/check-atp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.canFulfillAll", is(false)))
                .andExpect(jsonPath("$.data.items[0].isSufficient", is(true)))
                .andExpect(jsonPath("$.data.items[0].shortageQuantity", is(0)))
                .andExpect(jsonPath("$.data.items[1].isSufficient", is(false)))
                .andExpect(jsonPath("$.data.items[1].shortageQuantity", is(5.0)));
    }

    @Test
    @DisplayName("Quy trình Xác nhận đơn bán hàng: DRAFT -> CONFIRMED khi đủ tồn ATP")
    void confirmSalesOrderWorkflow() throws Exception {
        // Tạo đơn trạng thái DRAFT
        SalesOrderItemRequest item = new SalesOrderItemRequest(
                501L, "SKU-CONFIRM", "Sản phẩm", "Cái",
                new BigDecimal("10.000"), new BigDecimal("100000.00"),
                BigDecimal.ZERO, null,
                BigDecimal.ZERO, null,
                null
        );

        CreateSalesOrderRequest request = new CreateSalesOrderRequest(
                null,
                sampleCustomer.getId(),
                1L,
                "Kho Tổng Hà Nội",
                "Hà Nội",
                "Người nhận",
                "0987654321",
                LocalDate.now(),
                null,
                "Đơn nháp",
                OrderStatus.DRAFT,
                true,
                List.of(item)
        );

        String createRes = mockMvc.perform(post("/api/orders/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createRes).path("data").path("id").asLong();

        // Xác nhận đơn: DRAFT -> CONFIRMED
        mockMvc.perform(post("/api/orders/sales/{id}/confirm", orderId)
                        .header("X-User-Name", "manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.confirmedBy", is("manager")));
    }

    @Test
    @DisplayName("Hủy đơn bán hàng: chuyển sang CANCELLED")
    void cancelSalesOrderWorkflow() throws Exception {
        SalesOrderItemRequest item = new SalesOrderItemRequest(
                601L, "SKU-CANCEL", "Sản phẩm", "Cái",
                new BigDecimal("5.000"), new BigDecimal("100000.00"),
                BigDecimal.ZERO, null,
                BigDecimal.ZERO, null,
                null
        );

        CreateSalesOrderRequest request = new CreateSalesOrderRequest(
                null,
                sampleCustomer.getId(),
                1L,
                "Kho Tổng Hà Nội",
                "Hà Nội",
                "Người nhận",
                "0987654321",
                LocalDate.now(),
                null,
                "Đơn sắp hủy",
                OrderStatus.CONFIRMED,
                true,
                List.of(item)
        );

        String createRes = mockMvc.perform(post("/api/orders/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createRes).path("data").path("id").asLong();

        // Hủy đơn
        mockMvc.perform(post("/api/orders/sales/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("Báo cáo tổng quan thống kê đơn bán hàng: /api/orders/sales/summary")
    void salesOrderSummaryMetrics() throws Exception {
        mockMvc.perform(get("/api/orders/sales/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalOrders").isNumber())
                .andExpect(jsonPath("$.data.confirmedOrders").isNumber())
                .andExpect(jsonPath("$.data.totalRevenue").isNumber());
    }
}
