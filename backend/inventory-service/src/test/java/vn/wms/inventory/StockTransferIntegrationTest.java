package vn.wms.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.CreateStockTransferItemRequest;
import vn.wms.inventory.dto.CreateStockTransferRequest;
import vn.wms.inventory.dto.ReceiveStockTransferItemRequest;
import vn.wms.inventory.dto.ReceiveStockTransferRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.StockTransferItemRepository;
import vn.wms.inventory.repository.StockTransferRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp REST API luồng điều chuyển hàng hóa nội bộ giữa các kho (WMS-13.1, TASK-70).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockTransferIntegrationTest {

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
    private StockTransferRepository stockTransferRepository;

    @Autowired
    private StockTransferItemRepository stockTransferItemRepository;

    private Warehouse warehouseSource;
    private Warehouse warehouseDest;
    private BinLocation binSource;
    private BinLocation binDest;
    private final Long testProductId = 6001L;

    @BeforeEach
    void setUp() {
        cleanUp();

        // Thiết lập dữ liệu ban đầu cho Company A
        TenantContext.runAs(COMPANY_A, () -> {
            warehouseSource = warehouseRepository.save(
                    new Warehouse("WH-SRC-01", "Kho Nguồn A", "Hà Nội", "Quản lý Nguồn", "0911", 500)
            );
            warehouseDest = warehouseRepository.save(
                    new Warehouse("WH-DST-01", "Kho Đích A", "Đà Nẵng", "Quản lý Đích", "0922", 500)
            );

            binSource = binLocationRepository.save(
                    new BinLocation(warehouseSource, "ZA", "R01", "B01", new BigDecimal("1000.00"))
            );

            binDest = binLocationRepository.save(
                    new BinLocation(warehouseDest, "ZB", "R01", "B01", new BigDecimal("1000.00"))
            );

            // Nạp tồn kho ban đầu: 100 cái tại kho nguồn
            StockItem stockItem = new StockItem();
            stockItem.setProductId(testProductId);
            stockItem.setBinLocation(binSource);
            stockItem.setQuantity(new BigDecimal("100.000"));
            stockItem.setReservedQuantity(BigDecimal.ZERO);
            stockItemRepository.save(stockItem);
        });
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        TenantContext.clear();
    }

    private void cleanUp() {
        TenantContext.runAs(COMPANY_A, () -> {
            stockTransferItemRepository.deleteAll();
            stockTransferRepository.deleteAll();
            stockReservationRepository.deleteAll();
            stockItemRepository.deleteAll();
            binLocationRepository.deleteAll();
            warehouseRepository.deleteAll();
        });
        TenantContext.runAs(COMPANY_B, () -> {
            stockTransferItemRepository.deleteAll();
            stockTransferRepository.deleteAll();
            stockReservationRepository.deleteAll();
            stockItemRepository.deleteAll();
            binLocationRepository.deleteAll();
            warehouseRepository.deleteAll();
        });
    }

    @Test
    @DisplayName("End-to-End: Tạo lệnh DRAFT, Tra cứu danh sách, Xem chi tiết, Duyệt xuất kho (IN_TRANSIT) trừ tồn kho an toàn")
    void endToEnd_CreateListDetailAndDispatchTransfer() throws Exception {
        CreateStockTransferItemRequest itemRequest = new CreateStockTransferItemRequest(
                testProductId, "SKU-TEST-6001", "Dầu gội Clear", "Chai", binSource.getId(), new BigDecimal("30.000"), "Chuyển kho HN -> ĐN"
        );
        CreateStockTransferRequest createRequest = new CreateStockTransferRequest(
                warehouseSource.getId(), warehouseDest.getId(), Instant.now(), "Lệnh điều chuyển hàng số 1", List.of(itemRequest)
        );

        // 1. Tạo lệnh điều chuyển mới (POST /api/inventory/transfers)
        String createRes = mockMvc.perform(post("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.sourceWarehouseCode", is("WH-SRC-01")))
                .andExpect(jsonPath("$.data.destinationWarehouseCode", is("WH-DST-01")))
                .andExpect(jsonPath("$.data.totalQuantity", is(30.0)))
                .andReturn().getResponse().getContentAsString();

        Long transferId = objectMapper.readTree(createRes).path("data").path("id").asLong();
        assertThat(transferId).isNotNull().isGreaterThan(0);

        // 2. Tra cứu danh sách lệnh điều chuyển (GET /api/inventory/transfers)
        mockMvc.perform(get("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(transferId.intValue())))
                .andExpect(jsonPath("$.data.content[0].status", is("DRAFT")));

        // 3. Tra cứu chi tiết một lệnh điều chuyển (GET /api/inventory/transfers/{id})
        mockMvc.perform(get("/api/inventory/transfers/" + transferId)
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(transferId.intValue())))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].skuCode", is("SKU-TEST-6001")));

        // 4. Duyệt xuất kho chuyển đi đường (POST /api/inventory/transfers/{id}/dispatch)
        mockMvc.perform(post("/api/inventory/transfers/" + transferId + "/dispatch")
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("IN_TRANSIT")))
                .andExpect(jsonPath("$.data.totalTransferredQuantity", is(30.0)))
                .andExpect(jsonPath("$.data.transferDate").isNotEmpty());

        // 5. Kiểm tra tồn kho tại kho nguồn: Đã trừ 30, còn đúng 70.000
        StockItem reloadedStock = TenantContext.callAs(COMPANY_A, () ->
                stockItemRepository.findByBinLocationId(binSource.getId()).stream()
                        .filter(s -> s.getProductId().equals(testProductId))
                        .findFirst().orElseThrow());
        assertThat(reloadedStock.getQuantity()).isEqualByComparingTo(new BigDecimal("70.000"));
    }

    @Test
    @DisplayName("Duyệt xuất lệnh điều chuyển không đụng tới giữ chỗ của đơn bán có ID trùng ID phiếu điều chuyển")
    void dispatchTransfer_DoesNotTouchSalesOrderReservationWithSameId() throws Exception {
        CreateStockTransferItemRequest itemRequest = new CreateStockTransferItemRequest(
                testProductId, "SKU-TEST-6001", "Dầu gội Clear", "Chai", binSource.getId(), new BigDecimal("30.000"), null
        );
        CreateStockTransferRequest createRequest = new CreateStockTransferRequest(
                warehouseSource.getId(), warehouseDest.getId(), Instant.now(), "Lệnh trùng số với đơn bán", List.of(itemRequest)
        );

        String createRes = mockMvc.perform(post("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long transferId = objectMapper.readTree(createRes).path("data").path("id").asLong();

        // Đơn bán có orderId trùng ID phiếu điều chuyển đang giữ chỗ 20 cái cùng sản phẩm, cùng dòng tồn kho
        Long reservationId = TenantContext.callAs(COMPANY_A, () -> {
            StockItem stockItem = stockItemRepository.findByBinLocationId(binSource.getId()).get(0);
            stockItem.setReservedQuantity(new BigDecimal("20.000"));
            stockItem = stockItemRepository.save(stockItem);

            StockReservation reservation = new StockReservation();
            reservation.setOrderId(transferId);
            reservation.setOrderNumber("SO-20261109-0001");
            reservation.setProductId(testProductId);
            reservation.setStockItem(stockItem);
            reservation.setWarehouseId(warehouseSource.getId());
            reservation.setReservedQuantity(new BigDecimal("20.000"));
            reservation.setStatus("ACTIVE");
            return stockReservationRepository.save(reservation).getId();
        });

        mockMvc.perform(post("/api/inventory/transfers/" + transferId + "/dispatch")
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("IN_TRANSIT")));

        // Giữ chỗ của đơn bán vẫn ACTIVE, đủ 20 cái; Reserved của dòng tồn kho không đổi, chỉ OnHand giảm 30
        StockReservation reservationAfter = TenantContext.callAs(COMPANY_A,
                () -> stockReservationRepository.findById(reservationId).orElseThrow());
        assertThat(reservationAfter.getStatus()).isEqualTo("ACTIVE");
        assertThat(reservationAfter.getReservedQuantity()).isEqualByComparingTo(new BigDecimal("20.000"));

        StockItem stockAfter = TenantContext.callAs(COMPANY_A,
                () -> stockItemRepository.findByBinLocationId(binSource.getId()).get(0));
        assertThat(stockAfter.getReservedQuantity()).isEqualByComparingTo(new BigDecimal("20.000"));
        assertThat(stockAfter.getQuantity()).isEqualByComparingTo(new BigDecimal("70.000"));
    }

    @Test
    @DisplayName("Hủy lệnh điều chuyển khi còn ở trạng thái DRAFT")
    void cancelTransfer_Success() throws Exception {
        CreateStockTransferItemRequest itemRequest = new CreateStockTransferItemRequest(
                testProductId, "SKU-TEST-6001", "Dầu gội Clear", "Chai", binSource.getId(), new BigDecimal("10.000"), null
        );
        CreateStockTransferRequest createRequest = new CreateStockTransferRequest(
                warehouseSource.getId(), warehouseDest.getId(), Instant.now(), "Lệnh sắp hủy", List.of(itemRequest)
        );

        String createRes = mockMvc.perform(post("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long transferId = objectMapper.readTree(createRes).path("data").path("id").asLong();

        // Hủy lệnh
        mockMvc.perform(delete("/api/inventory/transfers/" + transferId)
                        .param("reason", "Hủy theo yêu cầu khách hàng")
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.notes").value(org.hamcrest.Matchers.containsString("Hủy theo yêu cầu")));
    }

    @Test
    @DisplayName("Cách ly đa doanh nghiệp: Doanh nghiệp B không thể xem, sửa hoặc duyệt xuất lệnh của Doanh nghiệp A")
    void crossTenantIsolation_CompanyBCannotAccessOrDispatchTransferOfCompanyA() throws Exception {
        CreateStockTransferItemRequest itemRequest = new CreateStockTransferItemRequest(
                testProductId, "SKU-TEST-6001", "Dầu gội Clear", "Chai", binSource.getId(), new BigDecimal("15.000"), null
        );
        CreateStockTransferRequest createRequest = new CreateStockTransferRequest(
                warehouseSource.getId(), warehouseDest.getId(), Instant.now(), "Lệnh của A", List.of(itemRequest)
        );

        String createRes = mockMvc.perform(post("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long transferIdOfA = objectMapper.readTree(createRes).path("data").path("id").asLong();

        // Company B liệt kê danh sách: Rỗng hoàn toàn
        mockMvc.perform(get("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        // Company B tra cứu chi tiết lệnh của A theo ID -> nhận 404 NOT FOUND
        mockMvc.perform(get("/api/inventory/transfers/" + transferIdOfA)
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());

        // Company B cố gắng duyệt xuất lệnh của A -> nhận 404 NOT FOUND
        mockMvc.perform(post("/api/inventory/transfers/" + transferIdOfA + "/dispatch")
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());

        // Company B cố gắng nhận hàng lệnh của A -> nhận 404 NOT FOUND
        ReceiveStockTransferItemRequest dummyItem = new ReceiveStockTransferItemRequest(
                1L, BigDecimal.ONE, BigDecimal.ZERO, 1L, null
        );
        ReceiveStockTransferRequest dummyReceiveRequest = new ReceiveStockTransferRequest(List.of(dummyItem), null);
        mockMvc.perform(post("/api/inventory/transfers/" + transferIdOfA + "/receive")
                        .header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dummyReceiveRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("End-to-End WMS-13.2: Nhận hàng đủ 100% tại kho đích -> COMPLETED, tăng tồn kho đích chính xác")
    void receiveTransfer_FullShipment_Completed() throws Exception {
        // 1. Tạo lệnh điều chuyển
        CreateStockTransferItemRequest itemRequest = new CreateStockTransferItemRequest(
                testProductId, "SKU-TEST-6001", "Dầu gội Clear", "Chai", binSource.getId(), new BigDecimal("30.000"), null
        );
        CreateStockTransferRequest createRequest = new CreateStockTransferRequest(
                warehouseSource.getId(), warehouseDest.getId(), Instant.now(), "Điều chuyển toàn bộ 30 chai", List.of(itemRequest)
        );

        String createRes = mockMvc.perform(post("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long transferId = objectMapper.readTree(createRes).path("data").path("id").asLong();
        Long transferItemId = objectMapper.readTree(createRes).path("data").path("items").get(0).path("id").asLong();

        // 2. Xuất kho đi đường (IN_TRANSIT)
        mockMvc.perform(post("/api/inventory/transfers/" + transferId + "/dispatch")
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("IN_TRANSIT")));

        // 3. Nhận hàng tại kho đích (POST /api/inventory/transfers/{id}/receive)
        ReceiveStockTransferItemRequest receiveItem = new ReceiveStockTransferItemRequest(
                transferItemId, new BigDecimal("30.000"), BigDecimal.ZERO, binDest.getId(), "Nhận đủ 30 chai"
        );
        ReceiveStockTransferRequest receiveRequest = new ReceiveStockTransferRequest(
                List.of(receiveItem), "Đã nhận đủ hàng tại kho đích"
        );

        mockMvc.perform(post("/api/inventory/transfers/" + transferId + "/receive")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.totalReceivedQuantity", is(30.0)))
                .andExpect(jsonPath("$.data.receivedBy").isNotEmpty())
                .andExpect(jsonPath("$.data.receivedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.notes").value(org.hamcrest.Matchers.containsString("Đã nhận đủ hàng")));

        // 4. Kiểm tra tồn kho tại kho đích: Đã tăng thêm đúng 30 chai
        StockItem destStock = TenantContext.callAs(COMPANY_A, () ->
                stockItemRepository.findByBinLocationId(binDest.getId()).stream()
                        .filter(s -> s.getProductId().equals(testProductId))
                        .findFirst().orElseThrow());
        assertThat(destStock.getQuantity()).isEqualByComparingTo(new BigDecimal("30.000"));
    }

    @Test
    @DisplayName("End-to-End WMS-13.2: Nhận hàng có hư hỏng/thiếu -> PARTIAL_RECEIVED kèm biên bản, cập nhật tồn kho theo số thực nhận")
    void receiveTransfer_PartialWithDamage_PartialReceived() throws Exception {
        // 1. Tạo lệnh điều chuyển 20 chai
        CreateStockTransferItemRequest itemRequest = new CreateStockTransferItemRequest(
                testProductId, "SKU-TEST-6001", "Dầu gội Clear", "Chai", binSource.getId(), new BigDecimal("20.000"), null
        );
        CreateStockTransferRequest createRequest = new CreateStockTransferRequest(
                warehouseSource.getId(), warehouseDest.getId(), Instant.now(), "Điều chuyển 20 chai", List.of(itemRequest)
        );

        String createRes = mockMvc.perform(post("/api/inventory/transfers")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long transferId = objectMapper.readTree(createRes).path("data").path("id").asLong();
        Long transferItemId = objectMapper.readTree(createRes).path("data").path("items").get(0).path("id").asLong();

        // 2. Xuất kho đi đường (IN_TRANSIT)
        mockMvc.perform(post("/api/inventory/transfers/" + transferId + "/dispatch")
                        .header("X-Company-Id", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("IN_TRANSIT")));

        // 3. Nhận hàng tại kho đích: Thực nhận 15, hư hỏng 5 do rơi vỡ
        ReceiveStockTransferItemRequest receiveItem = new ReceiveStockTransferItemRequest(
                transferItemId, new BigDecimal("15.000"), new BigDecimal("5.000"), binDest.getId(), "5 chai vỡ bung nắp"
        );
        ReceiveStockTransferRequest receiveRequest = new ReceiveStockTransferRequest(
                List.of(receiveItem), "Biên bản sự cố: 5 chai bị vỡ bung nắp trong thùng carton"
        );

        mockMvc.perform(post("/api/inventory/transfers/" + transferId + "/receive")
                        .header("X-Company-Id", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PARTIAL_RECEIVED")))
                .andExpect(jsonPath("$.data.totalReceivedQuantity", is(15.0)))
                .andExpect(jsonPath("$.data.items[0].damagedQuantity", is(5.0)))
                .andExpect(jsonPath("$.data.items[0].notes", is("5 chai vỡ bung nắp")))
                .andExpect(jsonPath("$.data.notes").value(org.hamcrest.Matchers.containsString("5 chai bị vỡ bung nắp")));

        // 4. Tồn kho kho đích chỉ tăng đúng 15 chai thực nhận (hàng hỏng không nhập kho tồn khả dụng)
        StockItem destStock = TenantContext.callAs(COMPANY_A, () ->
                stockItemRepository.findByBinLocationId(binDest.getId()).stream()
                        .filter(s -> s.getProductId().equals(testProductId))
                        .findFirst().orElseThrow());
        assertThat(destStock.getQuantity()).isEqualByComparingTo(new BigDecimal("15.000"));
    }
}

