package vn.wms.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.inventory.client.ProductClient;
import vn.wms.inventory.dto.CreateBinLocationRequest;
import vn.wms.inventory.dto.CreateWarehouseRequest;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khởi động context thật với H2; mỗi test chạy trong transaction và tự rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventoryServiceApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductClient productClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void feignClientToProductServiceIsRegistered() {
        assertThat(productClient).isNotNull();
    }

    @Test
    void listWarehousesReturnsEmptyListWhenNoData() throws Exception {
        mockMvc.perform(get("/api/inventory/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listWarehousesReturnsSavedWarehousesOrderedByCode() throws Exception {
        warehouseRepository.save(newWarehouse("WH02", "Kho Đà Nẵng"));
        warehouseRepository.save(newWarehouse("WH01", "Kho Hà Nội"));

        mockMvc.perform(get("/api/inventory/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("WH01"))
                .andExpect(jsonPath("$.data[1].name").value("Kho Đà Nẵng"));
    }

    @Test
    void createWarehouseAndGetSummaryShouldSucceed() throws Exception {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH-CANT-01", "Kho Cần Thơ", "KCN Trà Nóc", "Lê Văn B", "0912345678", 450, true);

        mockMvc.perform(post("/api/inventory/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("WH-CANT-01"))
                .andExpect(jsonPath("$.data.capacity").value(450));

        mockMvc.perform(get("/api/inventory/warehouses/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalWarehouses").value(1))
                .andExpect(jsonPath("$.data.totalCapacity").value(450));
    }

    @Test
    void createBinLocationAndFetchBarcodePreviewShouldSucceed() throws Exception {
        Warehouse warehouse = warehouseRepository.save(newWarehouse("WH09", "Kho Hải Phòng"));

        CreateBinLocationRequest locRequest = new CreateBinLocationRequest(
                warehouse.getId(), "ZA", "R01", "B01", new BigDecimal("500.00"), true);

        String createResult = mockMvc.perform(post("/api/inventory/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("WH09-ZA-R01-B01"))
                .andReturn().getResponse().getContentAsString();

        Long locationId = objectMapper.readTree(createResult).path("data").path("id").asLong();

        mockMvc.perform(get("/api/inventory/locations/" + locationId + "/barcode/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("WH09-ZA-R01-B01"))
                .andExpect(jsonPath("$.data.barcodeBase64").isNotEmpty());
    }

    @Test
    void livenessProbeIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private static Warehouse newWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        return warehouse;
    }
}
