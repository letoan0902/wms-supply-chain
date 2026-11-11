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
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.GeneratePickingListRequest;
import vn.wms.inventory.dto.PickingItemDemand;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PickingListIntegrationTest {

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

    private Warehouse warehouse;
    private BinLocation binA1;
    private BinLocation binA2;
    private BinLocation binB1;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);

        warehouse = new Warehouse();
        warehouse.setCode("WH-PICK-TEST");
        warehouse.setName("Kho Thử Nghiệm Picking");
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        binA1 = createBin("ZONE-A", "01", "01", "A-01-01");
        binA2 = createBin("ZONE-A", "02", "01", "A-02-01");
        binB1 = createBin("ZONE-B", "01", "01", "B-01-01");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private BinLocation createBin(String zone, String rack, String bin, String code) {
        BinLocation b = new BinLocation();
        b.setWarehouse(warehouse);
        b.setZone(zone);
        b.setRack(rack);
        b.setBin(bin);
        b.setCode(code);
        b.setActive(true);
        b.setStatus("AVAILABLE");
        return binLocationRepository.save(b);
    }

    private StockItem createStock(BinLocation bin, Long productId, String sku, String lotNo,
                                  BigDecimal qty, LocalDate expDate, boolean locked) {
        StockItem s = new StockItem();
        s.setBinLocation(bin);
        s.setProductId(productId);
        s.setLotNo(lotNo);
        s.setQuantity(qty);
        s.setReservedQuantity(BigDecimal.ZERO);
        s.setExpDate(expDate);
        s.setLocked(locked);
        return stockItemRepository.save(s);
    }

    @Test
    @DisplayName("Thuật toán gợi ý FEFO: Ưu tiên lô hạn dùng gần nhất (expDate ASC)")
    void pickingListPrioritizesFEFO() throws Exception {
        LocalDate today = LocalDate.now();
        Long productId = 100L;

        // Lô 1 ở binA2: hạn 60 ngày
        createStock(binA2, productId, "SKU-MILK", "LOT-EXP-60D", new BigDecimal("50"), today.plusDays(60), false);
        // Lô 2 ở binA1: hạn 15 ngày (gần hơn)
        createStock(binA1, productId, "SKU-MILK", "LOT-EXP-15D", new BigDecimal("30"), today.plusDays(15), false);

        GeneratePickingListRequest req = new GeneratePickingListRequest(
                1L, "SO-20261111-0001", warehouse.getId(),
                List.of(new PickingItemDemand(productId, "SKU-MILK", "Sữa tươi", "Hộp", new BigDecimal("20")))
        );

        mockMvc.perform(post("/api/inventory/stock/picking-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalLines", is(1)))
                .andExpect(jsonPath("$.data.items[0].lotNo", is("LOT-EXP-15D")))
                .andExpect(jsonPath("$.data.items[0].locationCode", is("A-01-01")))
                .andExpect(jsonPath("$.data.items[0].quantityToPick").value(20));
    }

    @Test
    @DisplayName("Tối ưu lộ trình ngắn nhất theo Zone -> Rack -> Bin")
    void pickingListSortsByShortestRoute() throws Exception {
        LocalDate today = LocalDate.now();

        // Sản phẩm 1 ở Zone B
        createStock(binB1, 101L, "SKU-101", "LOT-B1", new BigDecimal("10"), today.plusDays(30), false);
        // Sản phẩm 2 ở Zone A, Rack 02
        createStock(binA2, 102L, "SKU-102", "LOT-A2", new BigDecimal("10"), today.plusDays(30), false);
        // Sản phẩm 3 ở Zone A, Rack 01
        createStock(binA1, 103L, "SKU-103", "LOT-A1", new BigDecimal("10"), today.plusDays(30), false);

        GeneratePickingListRequest req = new GeneratePickingListRequest(
                2L, "SO-20261111-0002", warehouse.getId(),
                List.of(
                        new PickingItemDemand(101L, "SKU-101", "SP 1", "Cái", new BigDecimal("5")),
                        new PickingItemDemand(102L, "SKU-102", "SP 2", "Cái", new BigDecimal("5")),
                        new PickingItemDemand(103L, "SKU-103", "SP 3", "Cái", new BigDecimal("5"))
                )
        );

        mockMvc.perform(post("/api/inventory/stock/picking-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalLines", is(3)))
                // Chặng 1: Zone A, Rack 01
                .andExpect(jsonPath("$.data.items[0].step", is(1)))
                .andExpect(jsonPath("$.data.items[0].locationCode", is("A-01-01")))
                // Chặng 2: Zone A, Rack 02
                .andExpect(jsonPath("$.data.items[1].step", is(2)))
                .andExpect(jsonPath("$.data.items[1].locationCode", is("A-02-01")))
                // Chặng 3: Zone B, Rack 01
                .andExpect(jsonPath("$.data.items[2].step", is(3)))
                .andExpect(jsonPath("$.data.items[2].locationCode", is("B-01-01")));
    }

    @Test
    @DisplayName("Tự động loại bỏ các lô bị khóa hoặc đã hết hạn")
    void pickingListExcludesLockedAndExpiredLots() throws Exception {
        LocalDate today = LocalDate.now();
        Long productId = 104L;

        // Lô bị khóa
        createStock(binA1, productId, "SKU-104", "LOT-LOCKED", new BigDecimal("50"), today.plusDays(30), true);
        // Lô hết hạn hôm nay
        createStock(binA2, productId, "SKU-104", "LOT-EXPIRED", new BigDecimal("50"), today, false);
        // Lô hợp lệ
        createStock(binB1, productId, "SKU-104", "LOT-VALID", new BigDecimal("50"), today.plusDays(10), false);

        GeneratePickingListRequest req = new GeneratePickingListRequest(
                3L, "SO-20261111-0003", warehouse.getId(),
                List.of(new PickingItemDemand(productId, "SKU-104", "SP 4", "Cái", new BigDecimal("20")))
        );

        mockMvc.perform(post("/api/inventory/stock/picking-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalLines", is(1)))
                .andExpect(jsonPath("$.data.items[0].lotNo", is("LOT-VALID")))
                .andExpect(jsonPath("$.data.items[0].locationCode", is("B-01-01")));
    }
}
