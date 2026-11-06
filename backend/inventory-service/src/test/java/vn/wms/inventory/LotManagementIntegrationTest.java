package vn.wms.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.dto.LockLotRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.LotService;
import vn.wms.inventory.service.StockDeductionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LotManagementIntegrationTest {

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
    private InventoryLotRepository inventoryLotRepository;

    @Autowired
    private LotService lotService;

    @Autowired
    private StockDeductionService stockDeductionService;

    private Warehouse warehouse;
    private BinLocation bin1;
    private BinLocation bin2;
    private final Long testProductId = 9001L;

    @BeforeEach
    void setUp() {
        inventoryLotRepository.deleteAll();
        stockItemRepository.deleteAll();
        binLocationRepository.deleteAll();
        warehouseRepository.deleteAll();

        warehouse = new Warehouse();
        warehouse.setCode("WH-LOT-01");
        warehouse.setName("Kho Lô Hàng Thử Nghiệm");
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        bin1 = new BinLocation();
        bin1.setWarehouse(warehouse);
        bin1.setCode("WH-LOT-01-A-01-01");
        bin1.setZone("A");
        bin1.setRack("01");
        bin1.setBin("01");
        bin1.setActive(true);
        bin1 = binLocationRepository.save(bin1);

        bin2 = new BinLocation();
        bin2.setWarehouse(warehouse);
        bin2.setCode("WH-LOT-01-A-01-02");
        bin2.setZone("A");
        bin2.setRack("01");
        bin2.setBin("02");
        bin2.setActive(true);
        bin2 = binLocationRepository.save(bin2);
    }

    @Test
    @DisplayName("Ghi nhận Số lô (Batch), Ngày SX (Mfg Date), Hạn dùng (Exp Date) theo vị trí ô/kệ (FR12)")
    void recordLotWithMfgAndExpDate() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate mfg = today.minusMonths(1);
        LocalDate exp = today.plusMonths(6);

        StockItem item = new StockItem();
        item.setProductId(testProductId);
        item.setBinLocation(bin1);
        item.setLotNo("BATCH-202611-001");
        item.setMfgDate(mfg);
        item.setExpDate(exp);
        item.setQuantity(new BigDecimal("100.000"));
        item.setReservedQuantity(BigDecimal.ZERO);
        item.setLocked(false);
        stockItemRepository.save(item);

        mockMvc.perform(get("/api/inventory/lots")
                        .param("lotNo", "BATCH-202611-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].lotNo", is("BATCH-202611-001")))
                .andExpect(jsonPath("$.data.content[0].binLocationCode", is("WH-LOT-01-A-01-01")))
                .andExpect(jsonPath("$.data.content[0].mfgDate", is(mfg.toString())))
                .andExpect(jsonPath("$.data.content[0].expDate", is(exp.toString())))
                .andExpect(jsonPath("$.data.content[0].quantity", is(100.0)))
                .andExpect(jsonPath("$.data.content[0].locked", is(false)))
                .andExpect(jsonPath("$.data.content[0].expiryStatus", is("GOOD")));
    }

    @Test
    @DisplayName("Thuật toán xuất kho FEFO (First-Expired, First-Out): ưu tiên trừ lô có hạn dùng gần nhất trước")
    void fefoOrderingInStockDeduction() {
        LocalDate today = LocalDate.now();

        // Lô 1: Hạn dùng sau 10 ngày (cận hạn hơn) -> Vị trí bin2 (tạo sau)
        StockItem lotNearExpiry = new StockItem();
        lotNearExpiry.setProductId(testProductId);
        lotNearExpiry.setBinLocation(bin2);
        lotNearExpiry.setLotNo("LOT-EXP-NEAR");
        lotNearExpiry.setMfgDate(today.minusMonths(2));
        lotNearExpiry.setExpDate(today.plusDays(10));
        lotNearExpiry.setQuantity(new BigDecimal("50.000"));
        lotNearExpiry.setReservedQuantity(BigDecimal.ZERO);
        lotNearExpiry = stockItemRepository.save(lotNearExpiry);

        // Lô 2: Hạn dùng sau 90 ngày (xa hơn) -> Vị trí bin1 (tạo trước, id nhỏ hơn)
        StockItem lotFarExpiry = new StockItem();
        lotFarExpiry.setProductId(testProductId);
        lotFarExpiry.setBinLocation(bin1);
        lotFarExpiry.setLotNo("LOT-EXP-FAR");
        lotFarExpiry.setMfgDate(today.minusMonths(1));
        lotFarExpiry.setExpDate(today.plusDays(90));
        lotFarExpiry.setQuantity(new BigDecimal("50.000"));
        lotFarExpiry.setReservedQuantity(BigDecimal.ZERO);
        lotFarExpiry = stockItemRepository.save(lotFarExpiry);

        // Xuất kho 30 sản phẩm mà không chỉ định vị trí cụ thể -> Hệ thống tự động chọn theo FEFO
        DeductStockRequest req = new DeductStockRequest(
                101L,
                "SO-2026-FEFO-01",
                warehouse.getId(),
                "Xuất kho kiểm tra FEFO",
                null,
                List.of(new DeductStockItemRequest(testProductId, "SKU-FEFO-01", new BigDecimal("30.000"), null, null))
        );

        DeductStockResponse response = stockDeductionService.deductStock(req, "test_user");

        assertThat(response.totalDeductedQuantity()).isEqualByComparingTo("30.000");
        assertThat(response.deductedItems()).hasSize(1);
        // Kiểm tra lô được trừ chính là LOT-EXP-NEAR (FEFO) mặc dù id của nó lớn hơn
        assertThat(response.deductedItems().get(0).lotNo()).isEqualTo("LOT-EXP-NEAR");
        assertThat(response.deductedItems().get(0).binLocationCode()).isEqualTo("WH-LOT-01-A-01-02");

        // Kiểm tra tồn thực tế sau khi xuất
        StockItem nearAfter = stockItemRepository.findById(lotNearExpiry.getId()).orElseThrow();
        StockItem farAfter = stockItemRepository.findById(lotFarExpiry.getId()).orElseThrow();

        assertThat(nearAfter.getQuantity()).isEqualByComparingTo("20.000"); // 50 - 30 = 20
        assertThat(farAfter.getQuantity()).isEqualByComparingTo("50.000"); // 50 nguyên vẹn
    }

    @Test
    @DisplayName("Tự động khóa lô hết hạn (Exp Date <= hôm nay): tự động đổi locked = true và gán lý do (FR12)")
    void autoLockExpiredLots() throws Exception {
        LocalDate today = LocalDate.now();

        // Lô đã hết hạn từ hôm qua
        StockItem expiredLot = new StockItem();
        expiredLot.setProductId(testProductId);
        expiredLot.setBinLocation(bin1);
        expiredLot.setLotNo("LOT-EXPIRED-YESTERDAY");
        expiredLot.setExpDate(today.minusDays(1));
        expiredLot.setQuantity(new BigDecimal("40.000"));
        expiredLot.setLocked(false);
        expiredLot = stockItemRepository.save(expiredLot);

        // Lô còn hạn
        StockItem validLot = new StockItem();
        validLot.setProductId(testProductId);
        validLot.setBinLocation(bin2);
        validLot.setLotNo("LOT-VALID");
        validLot.setExpDate(today.plusMonths(3));
        validLot.setQuantity(new BigDecimal("60.000"));
        validLot.setLocked(false);
        validLot = stockItemRepository.save(validLot);

        // Kích hoạt API quét và khóa tự động
        mockMvc.perform(post("/api/inventory/lots/auto-lock-expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.lockedCount", is(1)));

        // Kiểm tra lô hết hạn đã bị khóa
        StockItem expiredAfter = stockItemRepository.findById(expiredLot.getId()).orElseThrow();
        assertThat(expiredAfter.isLocked()).isTrue();
        assertThat(expiredAfter.getLockedReason()).contains("hết hạn sử dụng");

        // Kiểm tra lô còn hạn không bị khóa
        StockItem validAfter = stockItemRepository.findById(validLot.getId()).orElseThrow();
        assertThat(validAfter.isLocked()).isFalse();
    }

    @Test
    @DisplayName("Chặn xuất kho đối với lô bị khóa và lô đã hết hạn sử dụng")
    void blockedDispatchForLockedAndExpiredLots() {
        LocalDate today = LocalDate.now();

        // Lô bị khóa thủ công
        StockItem lockedLot = new StockItem();
        lockedLot.setProductId(testProductId);
        lockedLot.setBinLocation(bin1);
        lockedLot.setLotNo("LOT-LOCKED-DEFECT");
        lockedLot.setQuantity(new BigDecimal("50.000"));
        lockedLot.setLocked(true);
        lockedLot.setLockedReason("Chờ kiểm định chất lượng");
        lockedLot = stockItemRepository.save(lockedLot);

        // Thử xuất từ vị trí ô kệ đang bị khóa -> Báo lỗi
        DeductStockRequest reqLocked = new DeductStockRequest(
                102L,
                "SO-FAIL-LOCKED",
                warehouse.getId(),
                "Thử xuất hàng bị khóa",
                null,
                List.of(new DeductStockItemRequest(testProductId, "SKU-TEST", new BigDecimal("10.000"), bin1.getId(), null))
        );

        assertThatThrownBy(() -> stockDeductionService.deductStock(reqLocked, "user1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đang bị khóa");

        // Lô hết hạn
        StockItem expiredLot = new StockItem();
        expiredLot.setProductId(testProductId);
        expiredLot.setBinLocation(bin2);
        expiredLot.setLotNo("LOT-EXP-PASSED");
        expiredLot.setExpDate(today.minusDays(2));
        expiredLot.setQuantity(new BigDecimal("50.000"));
        expiredLot.setLocked(false);
        expiredLot = stockItemRepository.save(expiredLot);

        // Thử xuất từ vị trí có lô hết hạn -> Báo lỗi
        DeductStockRequest reqExpired = new DeductStockRequest(
                103L,
                "SO-FAIL-EXPIRED",
                warehouse.getId(),
                "Thử xuất hàng hết hạn",
                null,
                List.of(new DeductStockItemRequest(testProductId, "SKU-TEST", new BigDecimal("10.000"), bin2.getId(), null))
        );

        assertThatThrownBy(() -> stockDeductionService.deductStock(reqExpired, "user1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hết hạn sử dụng");
    }

    @Test
    @DisplayName("Khóa và Mở khóa lô hàng thủ công qua REST API")
    void manualLockAndUnlockLot() throws Exception {
        LocalDate today = LocalDate.now();

        StockItem item = new StockItem();
        item.setProductId(testProductId);
        item.setBinLocation(bin1);
        item.setLotNo("LOT-MANUAL-01");
        item.setExpDate(today.plusMonths(5));
        item.setQuantity(new BigDecimal("80.000"));
        item.setLocked(false);
        item = stockItemRepository.save(item);

        // 1. Khóa lô hàng
        LockLotRequest lockReq = new LockLotRequest("Nghi ngờ hàng bị rách bao bì");
        mockMvc.perform(put("/api/inventory/lots/{id}/lock", item.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockReq))
                        .header("X-User-Name", "qlkho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.locked", is(true)))
                .andExpect(jsonPath("$.data.lockedReason", is("Nghi ngờ hàng bị rách bao bì")));

        // 2. Mở khóa lô hàng
        mockMvc.perform(put("/api/inventory/lots/{id}/unlock", item.getId())
                        .header("X-User-Name", "qlkho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.locked", is(false)))
                .andExpect(jsonPath("$.data.lockedReason").doesNotExist());
    }

    @Test
    @DisplayName("Báo cáo thống kê tổng quan tình trạng các lô hàng (/summary)")
    void lotSummaryMetrics() throws Exception {
        LocalDate today = LocalDate.now();

        // 1 lô hoạt động bình thường
        StockItem good = new StockItem();
        good.setProductId(testProductId);
        good.setBinLocation(bin1);
        good.setLotNo("LOT-GOOD");
        good.setExpDate(today.plusMonths(5));
        good.setQuantity(new BigDecimal("100.000"));
        good.setLocked(false);
        stockItemRepository.save(good);

        // 1 lô cận hạn (sau 15 ngày <= 30 ngày)
        StockItem near = new StockItem();
        near.setProductId(testProductId);
        near.setBinLocation(bin2);
        near.setLotNo("LOT-NEAR");
        near.setExpDate(today.plusDays(15));
        near.setQuantity(new BigDecimal("30.000"));
        near.setLocked(false);
        stockItemRepository.save(near);

        mockMvc.perform(get("/api/inventory/lots/summary")
                        .param("warehouseId", warehouse.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalLots", is(2)))
                .andExpect(jsonPath("$.data.activeLots", is(2)))
                .andExpect(jsonPath("$.data.expiringSoonLots", is(1)))
                .andExpect(jsonPath("$.data.expiredLots", is(0)))
                .andExpect(jsonPath("$.data.lockedLots", is(0)))
                .andExpect(jsonPath("$.data.totalQuantityInLots", is(130.0)));
    }

    @Test
    @DisplayName("Tra cứu danh sách lô hàng cận hạn sử dụng trong vòng 30 ngày (/expiring-soon)")
    void getExpiringSoonLots() throws Exception {
        LocalDate today = LocalDate.now();

        StockItem near = new StockItem();
        near.setProductId(testProductId);
        near.setBinLocation(bin1);
        near.setLotNo("LOT-EXPIRING-SOON");
        near.setExpDate(today.plusDays(20)); // Cận hạn trong 30 ngày
        near.setQuantity(new BigDecimal("45.000"));
        stockItemRepository.save(near);

        StockItem far = new StockItem();
        far.setProductId(testProductId);
        far.setBinLocation(bin2);
        far.setLotNo("LOT-EXPIRING-FAR");
        far.setExpDate(today.plusDays(120)); // Xa hơn 30 ngày
        far.setQuantity(new BigDecimal("55.000"));
        stockItemRepository.save(far);

        mockMvc.perform(get("/api/inventory/lots/expiring-soon")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("expiringDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].lotNo", is("LOT-EXPIRING-SOON")))
                .andExpect(jsonPath("$.data[0].daysUntilExpiry", is(20)));
    }

    @Test
    @DisplayName("Bảng inventory_lots: Lưu tồn chi tiết theo Lô & Vị trí ô (Bin ID), tìm xuất theo FEFO (WMS-10.2)")
    void inventoryLotsTable_recordsLotWithBinAndDates_andFindsByFefo() {
        LocalDate today = LocalDate.now();

        // Tạo 2 bản ghi trên bảng inventory_lots
        InventoryLot lot1 = new InventoryLot();
        lot1.setLotNumber("INV-LOT-FEFO-01");
        lot1.setProductId(testProductId);
        lot1.setBinLocation(bin1);
        lot1.setMfgDate(today.minusMonths(1));
        lot1.setExpDate(today.plusDays(10)); // Hạn gần hơn
        lot1.setQuantity(new BigDecimal("100.000"));
        lot1.setReservedQuantity(BigDecimal.ZERO);
        lot1.setLocked(false);
        inventoryLotRepository.save(lot1);

        InventoryLot lot2 = new InventoryLot();
        lot2.setLotNumber("INV-LOT-FEFO-02");
        lot2.setProductId(testProductId);
        lot2.setBinLocation(bin2);
        lot2.setMfgDate(today.minusMonths(1));
        lot2.setExpDate(today.plusDays(90)); // Hạn xa hơn
        lot2.setQuantity(new BigDecimal("200.000"));
        lot2.setReservedQuantity(BigDecimal.ZERO);
        lot2.setLocked(false);
        inventoryLotRepository.save(lot2);

        // Kiểm tra tra cứu theo FEFO
        List<InventoryLot> fefoLots = inventoryLotRepository.findAvailableLotsByFefo(testProductId, warehouse.getId());
        assertThat(fefoLots).hasSize(2);
        assertThat(fefoLots.get(0).getLotNumber()).isEqualTo("INV-LOT-FEFO-01");
        assertThat(fefoLots.get(1).getLotNumber()).isEqualTo("INV-LOT-FEFO-02");
    }

    @Test
    @DisplayName("Bảng inventory_lots: Tự động khóa lô hết hạn (Exp <= hôm nay) không cho phép xuất (WMS-10.2)")
    void inventoryLotsTable_autoLockExpiredLots() {
        LocalDate today = LocalDate.now();

        InventoryLot expiredLot = new InventoryLot();
        expiredLot.setLotNumber("INV-LOT-EXPIRED");
        expiredLot.setProductId(testProductId);
        expiredLot.setBinLocation(bin1);
        expiredLot.setMfgDate(today.minusMonths(6));
        expiredLot.setExpDate(today.minusDays(1)); // Đã hết hạn
        expiredLot.setQuantity(new BigDecimal("75.000"));
        expiredLot.setLocked(false);
        inventoryLotRepository.save(expiredLot);

        // Kích hoạt auto lock
        lotService.autoLockExpiredLots();

        InventoryLot after = inventoryLotRepository.findById(expiredLot.getId()).orElseThrow();
        assertThat(after.isLocked()).isTrue();
        assertThat(after.getLockedReason()).contains("hết hạn sử dụng");

        // Khi tìm kiếm lô khả dụng để xuất (findAvailableLotsByFefo), lô hết hạn/bị khóa phải bị loại bỏ
        List<InventoryLot> availableLots = inventoryLotRepository.findAvailableLotsByFefo(testProductId, warehouse.getId());
        assertThat(availableLots).isEmpty();
    }
}
