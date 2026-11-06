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
    private vn.wms.inventory.repository.BinLocationRepository binLocationRepository;

    @Autowired
    private vn.wms.inventory.repository.StockItemRepository stockItemRepository;

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

    @Test
    void repositoryWarehouseScopeQueriesWorkAsExpected() {
        Warehouse wh1 = warehouseRepository.save(newWarehouse("WH01", "Kho 1"));
        Warehouse wh2 = warehouseRepository.save(newWarehouse("WH02", "Kho 2"));
        Warehouse wh3 = warehouseRepository.save(newWarehouse("WH03", "Kho 3"));

        // 1. WarehouseRepository.findByIdInOrderByCodeAsc
        java.util.List<Warehouse> scopedWarehouses = warehouseRepository.findByIdInOrderByCodeAsc(java.util.List.of(wh1.getId(), wh3.getId()));
        assertThat(scopedWarehouses).hasSize(2);
        assertThat(scopedWarehouses).extracting(Warehouse::getCode).containsExactly("WH01", "WH03");

        // 2. BinLocationRepository.findByWarehouseIdInOrderByZoneAscRackAscBinAsc
        vn.wms.inventory.entity.BinLocation loc1 = new vn.wms.inventory.entity.BinLocation();
        loc1.setWarehouse(wh1);
        loc1.setZone("ZA");
        loc1.setRack("R01");
        loc1.setBin("B01");
        loc1.setCode("WH01-ZA-R01-B01");
        loc1.setStatus("EMPTY");
        loc1.setActive(true);
        binLocationRepository.save(loc1);

        vn.wms.inventory.entity.BinLocation loc2 = new vn.wms.inventory.entity.BinLocation();
        loc2.setWarehouse(wh2);
        loc2.setZone("ZB");
        loc2.setRack("R01");
        loc2.setBin("B01");
        loc2.setCode("WH02-ZB-R01-B01");
        loc2.setStatus("EMPTY");
        loc2.setActive(true);
        binLocationRepository.save(loc2);

        java.util.List<vn.wms.inventory.entity.BinLocation> scopedBins = binLocationRepository.findByWarehouseIdInOrderByZoneAscRackAscBinAsc(java.util.List.of(wh1.getId()));
        assertThat(scopedBins).hasSize(1);
        assertThat(scopedBins.get(0).getCode()).isEqualTo("WH01-ZA-R01-B01");
        assertThat(binLocationRepository.countByWarehouseIdIn(java.util.List.of(wh1.getId()))).isEqualTo(1);
        assertThat(binLocationRepository.countByWarehouseIdIn(java.util.List.of(wh1.getId(), wh2.getId()))).isEqualTo(2);

        // 3. StockItemRepository.sumQuantityByWarehouseIds
        vn.wms.inventory.entity.StockItem stock = new vn.wms.inventory.entity.StockItem();
        stock.setBinLocation(loc1);
        stock.setProductId(100L);
        stock.setQuantity(new BigDecimal("50.00"));
        stockItemRepository.save(stock);

        BigDecimal sumWh1 = stockItemRepository.sumQuantityByWarehouseIds(java.util.List.of(wh1.getId()));
        assertThat(sumWh1).isEqualByComparingTo("50.00");

        BigDecimal sumWh2 = stockItemRepository.sumQuantityByWarehouseIds(java.util.List.of(wh2.getId()));
        assertThat(sumWh2).isEqualByComparingTo("0");
    }

    @Test
    void endToEndWarehouseScopeFilteringAndIdorProtection() throws Exception {
        Warehouse wh1 = warehouseRepository.save(newWarehouse("WH-HN", "Kho Ha Noi"));
        Warehouse wh2 = warehouseRepository.save(newWarehouse("WH-SG", "Kho Sai Gon"));

        vn.wms.inventory.entity.BinLocation locWh1 = new vn.wms.inventory.entity.BinLocation();
        locWh1.setWarehouse(wh1);
        locWh1.setZone("ZA");
        locWh1.setRack("R01");
        locWh1.setBin("B01");
        locWh1.setCode("WH-HN-ZA-R01-B01");
        locWh1.setStatus("EMPTY");
        locWh1.setActive(true);
        binLocationRepository.save(locWh1);

        vn.wms.inventory.entity.BinLocation locWh2 = new vn.wms.inventory.entity.BinLocation();
        locWh2.setWarehouse(wh2);
        locWh2.setZone("ZB");
        locWh2.setRack("R01");
        locWh2.setBin("B01");
        locWh2.setCode("WH-SG-ZB-R01-B01");
        locWh2.setStatus("EMPTY");
        locWh2.setActive(true);
        binLocationRepository.save(locWh2);

        // KỊCH BẢN 1: Nhân viên chỉ được gán kho 1 (wh1)
        // 1.1 Xem danh sách kho -> Chỉ thấy kho 1
        mockMvc.perform(get("/api/inventory/warehouses")
                        .header("X-User-Name", "staff_hn")
                        .header("X-User-Roles", "WAREHOUSE_STAFF")
                        .header("X-User-Warehouse-Ids", String.valueOf(wh1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(wh1.getId()));

        // 1.2 Xem danh sách vị trí không truyền param -> Tự động chỉ thấy vị trí thuộc kho 1
        mockMvc.perform(get("/api/inventory/locations")
                        .header("X-User-Name", "staff_hn")
                        .header("X-User-Roles", "WAREHOUSE_STAFF")
                        .header("X-User-Warehouse-Ids", String.valueOf(wh1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(locWh1.getId()));

        // 1.3 Cố tình truyền param warehouseId=kho 2 (IDOR trên query param) -> Bị chặn 403 Forbidden
        mockMvc.perform(get("/api/inventory/locations?warehouseId=" + wh2.getId())
                        .header("X-User-Name", "staff_hn")
                        .header("X-User-Roles", "WAREHOUSE_STAFF")
                        .header("X-User-Warehouse-Ids", String.valueOf(wh1.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Bạn không có quyền truy cập dữ liệu của kho")));

        // 1.4 Cố tình xem chi tiết vị trí thuộc kho 2 (IDOR trên PathVariable GET /{id}) -> Bị chặn 403 Forbidden
        mockMvc.perform(get("/api/inventory/locations/" + locWh2.getId())
                        .header("X-User-Name", "staff_hn")
                        .header("X-User-Roles", "WAREHOUSE_STAFF")
                        .header("X-User-Warehouse-Ids", String.valueOf(wh1.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Bạn không có quyền thao tác trên dữ liệu của kho")));

        // 1.5 Cố tình tạo vị trí mới trong kho 2 (IDOR trên POST create) -> Bị chặn 403 Forbidden
        CreateBinLocationRequest illegalCreate = new CreateBinLocationRequest(
                wh2.getId(), "ZC", "R01", "B01", new BigDecimal("1000.00"), true);
        mockMvc.perform(post("/api/inventory/locations")
                        .header("X-User-Name", "staff_hn")
                        .header("X-User-Roles", "WAREHOUSE_STAFF")
                        .header("X-User-Warehouse-Ids", String.valueOf(wh1.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalCreate)))
                .andExpect(status().isForbidden());

        // KỊCH BẢN 2: Admin có quyền toàn cục (Global Scope)
        // 2.1 Xem tất cả vị trí mà không bị lọc
        mockMvc.perform(get("/api/inventory/locations")
                        .header("X-User-Name", "admin")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 2.2 Xem chi tiết vị trí kho 2 -> 200 OK
        mockMvc.perform(get("/api/inventory/locations/" + locWh2.getId())
                        .header("X-User-Name", "admin")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(locWh2.getId()));
    }

    private static Warehouse newWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        return warehouse;
    }
}
