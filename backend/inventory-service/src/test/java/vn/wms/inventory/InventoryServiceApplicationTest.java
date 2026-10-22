package vn.wms.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.inventory.client.ProductClient;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.WarehouseRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
