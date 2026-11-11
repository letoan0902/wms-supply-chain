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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.CreateWarehouseRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.IncomingStockRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vai trò INTEGRATION (hệ thống ngoài gọi bằng API key, WMS-25.1): gateway gắn X-Company-Id của key,
 * X-User-Roles: INTEGRATION và không gán kho nào. Vai trò này đọc được mọi kho của doanh nghiệp sở hữu key,
 * không đọc được dữ liệu doanh nghiệp khác và không ghi được gì.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationRoleAccessTest {

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

    private Long northOfA;
    private Long southOfA;
    private Long warehouseOfB;

    @BeforeEach
    void setUp() {
        cleanUpAllData();
        northOfA = TenantContext.callAs(COMPANY_A, () -> warehouseRepository.save(
                new Warehouse("WH01", "Kho miền Bắc của A", "Hà Nội", "Quản lý A", "0901111111", 500)).getId());
        southOfA = TenantContext.callAs(COMPANY_A, () -> warehouseRepository.save(
                new Warehouse("WH02", "Kho miền Nam của A", "TP.HCM", "Quản lý A", "0901111112", 300)).getId());
        warehouseOfB = TenantContext.callAs(COMPANY_B, () -> warehouseRepository.save(
                new Warehouse("WH01", "Kho của B", "Đà Nẵng", "Quản lý B", "0902222222", 800)).getId());
        TenantContext.runAs(COMPANY_A, () -> {
            binLocationRepository.save(new BinLocation(warehouseRepository.findById(northOfA).orElseThrow(),
                    "ZA", "R01", "B01", new BigDecimal("100.00")));
            binLocationRepository.save(new BinLocation(warehouseRepository.findById(southOfA).orElseThrow(),
                    "ZB", "R01", "B01", new BigDecimal("100.00")));
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
                incomingStockRepository.deleteAll();
                stockReservationRepository.deleteAll();
                stockItemRepository.deleteAll();
                binLocationRepository.deleteAll();
                warehouseRepository.deleteAll();
            });
        }
    }

    /** Header gateway gắn cho một request bằng API key của doanh nghiệp A. */
    private static MockHttpServletRequestBuilder asIntegrationOfA(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Company-Id", COMPANY_A)
                .header("X-User-Name", "apikey-12")
                .header("X-User-Roles", "INTEGRATION");
    }

    @Test
    @DisplayName("INTEGRATION đọc được mọi kho và vị trí của doanh nghiệp mình dù không được gán kho nào")
    void readsEveryWarehouseOfItsCompany() throws Exception {
        mockMvc.perform(asIntegrationOfA(get("/api/inventory/warehouses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name", containsInAnyOrder("Kho miền Bắc của A", "Kho miền Nam của A")));

        mockMvc.perform(asIntegrationOfA(get("/api/inventory/warehouses/" + southOfA)))
                .andExpect(status().isOk());

        mockMvc.perform(asIntegrationOfA(get("/api/inventory/locations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // So sánh: nhân viên kho chưa được gán kho thì bị từ chối (403), còn INTEGRATION không cần gán kho
        mockMvc.perform(get("/api/inventory/warehouses")
                        .header("X-Company-Id", COMPANY_A)
                        .header("X-User-Name", "staff01")
                        .header("X-User-Roles", "WAREHOUSE_STAFF"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("INTEGRATION không đọc được kho của doanh nghiệp khác (404)")
    void cannotReadOtherCompany() throws Exception {
        mockMvc.perform(asIntegrationOfA(get("/api/inventory/warehouses/" + warehouseOfB)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("INTEGRATION không ghi được: tạo, xóa kho đều nhận 403, dữ liệu không đổi")
    void cannotWrite() throws Exception {
        CreateWarehouseRequest request = new CreateWarehouseRequest("WH09", "Kho lạ", "HN", "X", "090", 10, true);
        mockMvc.perform(asIntegrationOfA(post("/api/inventory/warehouses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(asIntegrationOfA(delete("/api/inventory/warehouses/" + northOfA)))
                .andExpect(status().isForbidden());

        assertThat(TenantContext.callAs(COMPANY_A, () -> warehouseRepository.count())).isEqualTo(2);
    }
}
