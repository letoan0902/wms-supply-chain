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
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.LockLotRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.ExpiredLotScheduler;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cách ly lô hàng (inventory_lots) giữa hai doanh nghiệp và tác vụ định kỳ khóa lô hết hạn chạy cho từng doanh nghiệp
 * (WMS-10.2, WMS-22.4a). Lớp test KHÔNG đặt @Transactional để mỗi lời gọi mở một phiên làm việc riêng như request thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LotTenantIsolationTest {

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final Long PRODUCT_ID = 9101L;

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
    private ExpiredLotScheduler expiredLotScheduler;

    private Long lotIdOfA;
    private Long lotIdOfB;
    private Long stockItemIdOfA;

    @BeforeEach
    void setUp() {
        cleanUpAllData();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Doanh nghiệp A: một lô hết hạn trong inventory_lots và một lô hết hạn trong stock_items
        TenantContext.runAs(COMPANY_A, () -> {
            BinLocation bin = createBin("Kho lô của A");
            lotIdOfA = inventoryLotRepository.save(expiredLot("LOT-A-001", bin, yesterday)).getId();

            StockItem item = new StockItem();
            item.setProductId(PRODUCT_ID);
            item.setBinLocation(bin);
            item.setLotNo("LOT-A-STOCK");
            item.setExpDate(yesterday);
            item.setQuantity(new BigDecimal("30.000"));
            stockItemIdOfA = stockItemRepository.save(item).getId();
        });

        // Doanh nghiệp B: chỉ có một lô hết hạn trong inventory_lots (không có dòng stock_items nào)
        TenantContext.runAs(COMPANY_B, () -> {
            BinLocation bin = createBin("Kho lô của B");
            lotIdOfB = inventoryLotRepository.save(expiredLot("LOT-B-001", bin, yesterday)).getId();
        });
    }

    @AfterEach
    void tearDown() {
        cleanUpAllData();
        TenantContext.clear();
    }

    private void cleanUpAllData() {
        for (Long companyId : new Long[]{COMPANY_A, COMPANY_B}) {
            TenantContext.runAs(companyId, () -> {
                inventoryLotRepository.deleteAll();
                stockItemRepository.deleteAll();
                binLocationRepository.deleteAll();
                warehouseRepository.deleteAll();
            });
        }
    }

    private BinLocation createBin(String warehouseName) {
        Warehouse warehouse = warehouseRepository.save(
                new Warehouse("WH-LOT", warehouseName, "Hà Nội", "Quản lý", "0900000000", 500));
        return binLocationRepository.save(new BinLocation(warehouse, "ZL", "R01", "B01", new BigDecimal("1000.00")));
    }

    private static InventoryLot expiredLot(String lotNumber, BinLocation bin, LocalDate expDate) {
        InventoryLot lot = new InventoryLot();
        lot.setLotNumber(lotNumber);
        lot.setProductId(PRODUCT_ID);
        lot.setBinLocation(bin);
        lot.setMfgDate(expDate.minusMonths(6));
        lot.setExpDate(expDate);
        lot.setQuantity(new BigDecimal("50.000"));
        lot.setReservedQuantity(BigDecimal.ZERO);
        lot.setLocked(false);
        return lot;
    }

    private InventoryLot lotAs(Long companyId, Long lotId) {
        return TenantContext.callAs(companyId, () -> inventoryLotRepository.findById(lotId).orElseThrow());
    }

    @Test
    @DisplayName("B chỉ thấy lô của B, không khóa hay mở khóa được lô của A (404), tải lô của A theo ID cũng là 404")
    void companyBCannotListOrLockLotOfCompanyA() throws Exception {
        // B liệt kê lô: chỉ có lô của B
        mockMvc.perform(get("/api/inventory/lots").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].stockItemId", is(lotIdOfB.intValue())))
                .andExpect(jsonPath("$.data.content[0].lotNo", is("LOT-B-001")));

        // B khóa và mở khóa lô của A theo ID -> 404
        mockMvc.perform(put("/api/inventory/lots/{id}/lock", lotIdOfA)
                        .header("X-Company-Id", COMPANY_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LockLotRequest("B cố khóa lô của A"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/inventory/lots/{id}/unlock", lotIdOfA)
                        .header("X-Company-Id", COMPANY_B))
                .andExpect(status().isNotFound());

        // B tải lô của A theo khóa chính -> NOT_FOUND (TenantEntity chặn), giống API trả 404
        assertThatThrownBy(() -> lotAs(COMPANY_B, lotIdOfA))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        // Lô của A không bị thay đổi
        InventoryLot lotOfA = lotAs(COMPANY_A, lotIdOfA);
        assertThat(lotOfA.isLocked()).isFalse();
        assertThat(lotOfA.getLockedReason()).isNull();
        assertThat(lotOfA.getCompanyId()).isEqualTo(COMPANY_A);
    }

    @Test
    @DisplayName("API auto-lock-expired chỉ khóa lô hết hạn của doanh nghiệp người gọi")
    void autoLockEndpointOnlyLocksLotsOfCallerCompany() throws Exception {
        mockMvc.perform(post("/api/inventory/lots/auto-lock-expired").header("X-Company-Id", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockedCount", is(1)));

        assertThat(lotAs(COMPANY_B, lotIdOfB).isLocked()).isTrue();
        assertThat(lotAs(COMPANY_A, lotIdOfA).isLocked()).isFalse();
        assertThat(TenantContext.callAs(COMPANY_A,
                () -> stockItemRepository.findById(stockItemIdOfA).orElseThrow().isLocked())).isFalse();
    }

    @Test
    @DisplayName("Tác vụ định kỳ (không có doanh nghiệp trong ngữ cảnh) khóa lô hết hạn của cả hai doanh nghiệp")
    void scheduledEntryPointLocksExpiredLotsOfEveryCompany() {
        TenantContext.clear();

        int totalLocked = expiredLotScheduler.autoLockExpiredLotsForAllCompanies();

        assertThat(totalLocked).isGreaterThanOrEqualTo(3);
        assertThat(TenantContext.isSet()).isFalse();

        InventoryLot lotOfA = lotAs(COMPANY_A, lotIdOfA);
        assertThat(lotOfA.isLocked()).isTrue();
        assertThat(lotOfA.getLockedReason()).contains("hết hạn sử dụng");
        assertThat(TenantContext.callAs(COMPANY_A,
                () -> stockItemRepository.findById(stockItemIdOfA).orElseThrow().isLocked())).isTrue();

        InventoryLot lotOfB = lotAs(COMPANY_B, lotIdOfB);
        assertThat(lotOfB.isLocked()).isTrue();
        assertThat(lotOfB.getLockedReason()).contains("hết hạn sử dụng");
    }
}
