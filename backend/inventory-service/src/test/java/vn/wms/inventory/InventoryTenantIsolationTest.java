package vn.wms.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.CreateBinLocationRequest;
import vn.wms.inventory.dto.CreateWarehouseRequest;
import vn.wms.inventory.dto.UpdateWarehouseRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.messaging.OrderEventsListener;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.IncomingStockRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.BinLocationService;
import vn.wms.inventory.service.WarehouseService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra cách ly dữ liệu giữa hai doanh nghiệp (Company A và Company B) trong inventory-service (WMS-22.4a, TASK-136).
 * Lớp test KHÔNG đặt @Transactional để mỗi lời gọi MockMvc/Repository mở một phiên làm việc riêng biệt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryTenantIsolationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private BinLocationRepository binLocationRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private IncomingStockRepository incomingStockRepository;

    @Autowired
    private WarehouseService warehouseService;

    @Autowired
    private BinLocationService binLocationService;

    @Autowired
    private OrderEventsListener orderEventsListener;

    private Long warehouseIdOfA;
    private Long warehouseIdOfB;
    private Long binLocationIdOfA;
    private Long binLocationIdOfB;

    @BeforeEach
    void setUp() {
        cleanUpAllData();

        // 1. Doanh nghiệp A tạo kho WH01
        warehouseIdOfA = TenantContext.callAs(COMPANY_A, () -> {
            Warehouse w = new Warehouse("WH01", "Kho Tổng Miền Bắc (Doanh nghiệp A)", "Hà Nội", "Quản lý A", "0901111111", 500);
            return warehouseRepository.save(w).getId();
        });

        // 2. Doanh nghiệp B tạo kho WH01 (trùng mã code với kho của A)
        warehouseIdOfB = TenantContext.callAs(COMPANY_B, () -> {
            Warehouse w = new Warehouse("WH01", "Kho Tổng Miền Nam (Doanh nghiệp B)", "TP.HCM", "Quản lý B", "0902222222", 800);
            return warehouseRepository.save(w).getId();
        });

        // 3. Tạo vị trí ô/kệ cho mỗi bên
        binLocationIdOfA = TenantContext.callAs(COMPANY_A, () -> {
            Warehouse wa = warehouseRepository.findById(warehouseIdOfA).orElseThrow();
            BinLocation loc = new BinLocation(wa, "ZA", "R01", "B01", new BigDecimal("1000.00"));
            return binLocationRepository.save(loc).getId();
        });

        binLocationIdOfB = TenantContext.callAs(COMPANY_B, () -> {
            Warehouse wb = warehouseRepository.findById(warehouseIdOfB).orElseThrow();
            BinLocation loc = new BinLocation(wb, "ZA", "R01", "B01", new BigDecimal("1500.00"));
            return binLocationRepository.save(loc).getId();
        });
    }

    @AfterEach
    void tearDown() {
        cleanUpAllData();
        TenantContext.clear();
    }

    private void cleanUpAllData() {
        TenantContext.runAs(COMPANY_A, () -> {
            incomingStockRepository.deleteAll();
            stockReservationRepository.deleteAll();
            stockItemRepository.deleteAll();
            binLocationRepository.deleteAll();
            warehouseRepository.deleteAll();
        });
        TenantContext.runAs(COMPANY_B, () -> {
            incomingStockRepository.deleteAll();
            stockReservationRepository.deleteAll();
            stockItemRepository.deleteAll();
            binLocationRepository.deleteAll();
            warehouseRepository.deleteAll();
        });
    }

    @Test
    @DisplayName("Tiêu chí 1: Hai doanh nghiệp A và B cùng tạo kho mã WH01 - cả hai đều thành công")
    void duplicateWarehouseCodeAllowedAcrossDifferentCompanies() {
        Warehouse wa = TenantContext.callAs(COMPANY_A, () -> warehouseRepository.findById(warehouseIdOfA).orElseThrow());
        Warehouse wb = TenantContext.callAs(COMPANY_B, () -> warehouseRepository.findById(warehouseIdOfB).orElseThrow());

        assertThat(wa.getCode()).isEqualTo("WH01");
        assertThat(wa.getCompanyId()).isEqualTo(COMPANY_A);
        assertThat(wa.getName()).contains("Doanh nghiệp A");

        assertThat(wb.getCode()).isEqualTo("WH01");
        assertThat(wb.getCompanyId()).isEqualTo(COMPANY_B);
        assertThat(wb.getName()).contains("Doanh nghiệp B");

        // Tuy nhiên trong cùng một doanh nghiệp thì không được trùng mã kho
        CreateWarehouseRequest dupRequest = new CreateWarehouseRequest("WH01", "Kho Trùng", "HN", "A", "090", 100, true);
        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_A, () -> warehouseService.createWarehouse(dupRequest)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Hai doanh nghiệp A và B cùng tạo vị trí ô/kệ có mã giống nhau - cả hai đều thành công")
    void duplicateBinLocationCodeAllowedAcrossDifferentCompanies() {
        BinLocation locA = TenantContext.callAs(COMPANY_A, () -> binLocationRepository.findById(binLocationIdOfA).orElseThrow());
        BinLocation locB = TenantContext.callAs(COMPANY_B, () -> binLocationRepository.findById(binLocationIdOfB).orElseThrow());

        assertThat(locA.getCode()).isEqualTo("WH01-ZA-R01-B01");
        assertThat(locA.getCompanyId()).isEqualTo(COMPANY_A);

        assertThat(locB.getCode()).isEqualTo("WH01-ZA-R01-B01");
        assertThat(locB.getCompanyId()).isEqualTo(COMPANY_B);

        // Trong cùng 1 doanh nghiệp thì không được tạo trùng mã vị trí
        CreateBinLocationRequest dupLoc = new CreateBinLocationRequest(warehouseIdOfA, "ZA", "R01", "B01", new BigDecimal("500.00"), true);
        assertThatThrownBy(() -> TenantContext.runAs(COMPANY_A, () -> binLocationService.createLocation(dupLoc)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Tiêu chí 2: B liệt kê kho chỉ thấy kho của B, tra cứu ID kho của A nhận 404")
    void companyBCannotSeeOrAccessWarehouseOfCompanyA() throws Exception {
        // B liệt kê danh sách kho: chỉ trả về đúng 1 kho của B
        mockMvc.perform(get("/api/inventory/warehouses")
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(warehouseIdOfB.intValue())))
                .andExpect(jsonPath("$.data[0].name", is("Kho Tổng Miền Nam (Doanh nghiệp B)")));

        // B tra cứu chi tiết kho của A theo ID -> nhận 404 Not Found
        mockMvc.perform(get("/api/inventory/warehouses/" + warehouseIdOfA)
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tiêu chí 3: B sửa hoặc xóa kho của A bằng ID nhận 404, dữ liệu của A không đổi")
    void companyBCannotUpdateOrDeleteWarehouseOfCompanyA() throws Exception {
        UpdateWarehouseRequest updateRequest = new UpdateWarehouseRequest(
                "Tên Bị Đổi Bởi B", "Địa chỉ lạ", "Hacker", "0999999999", 9999, true);

        // B cố sửa kho của A -> 404
        mockMvc.perform(put("/api/inventory/warehouses/" + warehouseIdOfA)
                        .header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        // B cố xóa kho của A -> 404
        mockMvc.perform(delete("/api/inventory/warehouses/" + warehouseIdOfA)
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());

        // Xác minh dữ liệu của A hoàn toàn không bị ảnh hưởng
        Warehouse warehouseA = TenantContext.callAs(COMPANY_A, () -> warehouseRepository.findById(warehouseIdOfA).orElseThrow());
        assertThat(warehouseA.getName()).isEqualTo("Kho Tổng Miền Bắc (Doanh nghiệp A)");
        assertThat(warehouseA.getCapacity()).isEqualTo(500);
    }

    @Test
    @DisplayName("Cách ly vị trí ô/kệ: B liệt kê hoặc tra cứu vị trí của A đều nhận 404")
    void companyBCannotSeeOrAccessBinLocationsOfCompanyA() throws Exception {
        // B liệt kê vị trí: chỉ thấy vị trí của B
        mockMvc.perform(get("/api/inventory/locations")
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(binLocationIdOfB.intValue())));

        // B tra cứu vị trí của A -> 404
        mockMvc.perform(get("/api/inventory/locations/" + binLocationIdOfA)
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cách ly tồn kho & tồn khả dụng ATP giữa các doanh nghiệp")
    void stockAndAtpIsolationBetweenCompanies() throws Exception {
        Long testProductId = 9001L;

        // Tạo tồn kho 150 cái cho Company A tại kho của A
        TenantContext.runAs(COMPANY_A, () -> {
            BinLocation locA = binLocationRepository.findById(binLocationIdOfA).orElseThrow();
            StockItem stockA = new StockItem();
            stockA.setProductId(testProductId);
            stockA.setBinLocation(locA);
            stockA.setQuantity(new BigDecimal("150.000"));
            stockA.setReservedQuantity(new BigDecimal("20.000"));
            stockItemRepository.save(stockA);
        });

        // Company A tra cứu ATP của testProductId: OnHand = 150, Reserved = 20 -> ATP = 130
        mockMvc.perform(get("/api/inventory/stock/atp")
                        .param("productId", testProductId.toString())
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHandQuantity", is(150.0)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(20.0)))
                .andExpect(jsonPath("$.data.atpQuantity", is(130.0)));

        // Company B tra cứu cùng testProductId: Doanh nghiệp B chưa có tồn -> ATP = 0
        mockMvc.perform(get("/api/inventory/stock/atp")
                        .param("productId", testProductId.toString())
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHandQuantity", is(0)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(0)))
                .andExpect(jsonPath("$.data.atpQuantity", is(0)));
    }

    @Test
    @DisplayName("Endpoint nội bộ KPI Dashboard (WMS-16.1) chỉ tổng hợp tồn kho và ô/kệ của doanh nghiệp gọi")
    void internalKpiMetricsOnlyCountCallerCompany() throws Exception {
        TenantContext.runAs(COMPANY_A, () -> {
            StockItem stockA = new StockItem();
            stockA.setProductId(9101L);
            stockA.setBinLocation(binLocationRepository.findById(binLocationIdOfA).orElseThrow());
            stockA.setQuantity(new BigDecimal("150.000"));
            stockA.setReservedQuantity(new BigDecimal("20.000"));
            stockItemRepository.save(stockA);
        });
        TenantContext.runAs(COMPANY_B, () -> {
            StockItem stockB = new StockItem();
            stockB.setProductId(9101L);
            stockB.setBinLocation(binLocationRepository.findById(binLocationIdOfB).orElseThrow());
            stockB.setQuantity(new BigDecimal("400.000"));
            stockB.setReservedQuantity(BigDecimal.ZERO);
            stockItemRepository.save(stockB);
        });

        String bodyA = mockMvc.perform(get("/internal/inventory/kpi-metrics")
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occupiedLocations", is(1)))
                .andExpect(jsonPath("$.data.totalLocations", is(1)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var dataA = objectMapper.readTree(bodyA).path("data");
        assertThat(dataA.path("availableStockQuantity").decimalValue()).isEqualByComparingTo("130");
        assertThat(dataA.path("totalStockQuantity").decimalValue()).isEqualByComparingTo("150");

        // Kho của A truyền kèm X-Company-Id của B: không thấy gì của A
        String bodyB = mockMvc.perform(get("/internal/inventory/kpi-metrics")
                        .param("warehouseId", warehouseIdOfA.toString())
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLocations", is(0)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var dataB = objectMapper.readTree(bodyB).path("data");
        assertThat(dataB.path("availableStockQuantity").decimalValue()).isEqualByComparingTo("0");
        assertThat(dataB.path("totalStockQuantity").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Tiêu chí 4: Sự kiện order.created của A chỉ tạo hàng đang về (Incoming Stock) cho A")
    void orderCreatedEventForCompanyAOnlyCreatesIncomingStockForCompanyA() throws Exception {
        Long poOrderId = 7701L;
        Long poProductId = 9002L;

        OrderCreatedEvent event = new OrderCreatedEvent(
                poOrderId,
                "PO-20261109-0001",
                OrderType.PURCHASE,
                "APPROVED",
                10L,
                null,
                warehouseIdOfA,
                "Kho Tổng Miền Bắc",
                new BigDecimal("5000000.00"),
                "purchasing_user",
                Instant.now(),
                List.of(new OrderLine(poProductId, "SKU-PO-9002", "Bánh Cosy Mè", new BigDecimal("60.000"), "Hộp", BigDecimal.valueOf(50000)))
        );

        // Tạo thông điệp RabbitMQ mang header x-wms-company-id = COMPANY_A
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setReceivedRoutingKey(WmsEvents.RoutingKeys.ORDER_CREATED);
        properties.setMessageId("msg-po-isolated-001");
        properties.setHeader(WmsEventPublisher.HEADER_COMPANY_ID, COMPANY_A);
        Message message = new Message(objectMapper.writeValueAsBytes(event), properties);

        // Listener xử lý sự kiện
        orderEventsListener.onMessage(message);

        // Xác minh: Company A có 60 cái hàng đang về
        BigDecimal incomingQtyForA = TenantContext.callAs(COMPANY_A,
                () -> incomingStockRepository.calculateIncomingQuantity(warehouseIdOfA, poProductId));
        assertThat(incomingQtyForA).isEqualByComparingTo(new BigDecimal("60.000"));

        // Xác minh: Company B tra cứu hàng đang về của cùng SKU này -> hoàn toàn bằng 0
        BigDecimal incomingQtyForB = TenantContext.callAs(COMPANY_B,
                () -> incomingStockRepository.calculateTotalIncomingQuantityByProduct(poProductId));
        assertThat(incomingQtyForB).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

