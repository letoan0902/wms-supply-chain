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
import vn.wms.inventory.dto.BatchAtpRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockAtpTest {

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

    private Warehouse warehouse1;
    private Warehouse warehouse2;
    private BinLocation bin1;
    private BinLocation bin2;

    @BeforeEach
    void setUp() {
        warehouse1 = new Warehouse();
        warehouse1.setCode("WH-ATP-01");
        warehouse1.setName("Kho Hà Nội");
        warehouse1.setActive(true);
        warehouse1 = warehouseRepository.save(warehouse1);

        warehouse2 = new Warehouse();
        warehouse2.setCode("WH-ATP-02");
        warehouse2.setName("Kho TP.HCM");
        warehouse2.setActive(true);
        warehouse2 = warehouseRepository.save(warehouse2);

        bin1 = new BinLocation();
        bin1.setWarehouse(warehouse1);
        bin1.setCode("WH-ATP-01-A-01-01");
        bin1.setZone("A");
        bin1.setRack("01");
        bin1.setBin("01");
        bin1.setActive(true);
        bin1 = binLocationRepository.save(bin1);

        bin2 = new BinLocation();
        bin2.setWarehouse(warehouse2);
        bin2.setCode("WH-ATP-02-B-01-01");
        bin2.setZone("B");
        bin2.setRack("01");
        bin2.setBin("01");
        bin2.setActive(true);
        bin2 = binLocationRepository.save(bin2);
    }

    @Test
    @DisplayName("Kiểm tra tồn khả dụng ATP khi sản phẩm chưa có trong kho -> trả về 0")
    void testGetAtpWhenProductNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/stock/atp")
                        .param("productId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.productId", is(9999)))
                .andExpect(jsonPath("$.data.onHandQuantity", is(0)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(0)))
                .andExpect(jsonPath("$.data.atpQuantity", is(0)));
    }

    @Test
    @DisplayName("Kiểm tra tồn khả dụng ATP: OnHand = 100, Reserved = 30 -> ATP = 70")
    void testGetAtpCalculation() throws Exception {
        Long productId = 101L;

        StockItem item1 = new StockItem();
        item1.setProductId(productId);
        item1.setBinLocation(bin1);
        item1.setQuantity(new BigDecimal("100.000"));
        item1.setReservedQuantity(new BigDecimal("30.000"));
        stockItemRepository.save(item1);

        mockMvc.perform(get("/api/inventory/stock/atp")
                        .param("productId", productId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.productId", is(101)))
                .andExpect(jsonPath("$.data.onHandQuantity", is(100.0)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(30.0)))
                .andExpect(jsonPath("$.data.atpQuantity", is(70.0)));
    }

    @Test
    @DisplayName("Kiểm tra tồn khả dụng ATP lọc theo từng kho riêng biệt")
    void testGetAtpFilteredByWarehouse() throws Exception {
        Long productId = 102L;

        // Kho 1: OnHand = 50, Reserved = 10 -> ATP = 40
        StockItem item1 = new StockItem();
        item1.setProductId(productId);
        item1.setBinLocation(bin1);
        item1.setQuantity(new BigDecimal("50.000"));
        item1.setReservedQuantity(new BigDecimal("10.000"));
        stockItemRepository.save(item1);

        // Kho 2: OnHand = 80, Reserved = 20 -> ATP = 60
        StockItem item2 = new StockItem();
        item2.setProductId(productId);
        item2.setBinLocation(bin2);
        item2.setQuantity(new BigDecimal("80.000"));
        item2.setReservedQuantity(new BigDecimal("20.000"));
        stockItemRepository.save(item2);

        // Kiểm tra Kho 1
        mockMvc.perform(get("/api/inventory/stock/atp")
                        .param("productId", productId.toString())
                        .param("warehouseId", warehouse1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouseId", is(warehouse1.getId().intValue())))
                .andExpect(jsonPath("$.data.onHandQuantity", is(50.0)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(10.0)))
                .andExpect(jsonPath("$.data.atpQuantity", is(40.0)));

        // Kiểm tra toàn hệ thống (cả 2 kho): OnHand = 130, Reserved = 30 -> ATP = 100
        mockMvc.perform(get("/api/inventory/stock/atp")
                        .param("productId", productId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onHandQuantity", is(130.0)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(30.0)))
                .andExpect(jsonPath("$.data.atpQuantity", is(100.0)));
    }

    @Test
    @DisplayName("Kiểm tra Batch ATP cho danh sách nhiều sản phẩm")
    void testBatchAtp() throws Exception {
        Long p1 = 201L;
        Long p2 = 202L;

        StockItem item1 = new StockItem();
        item1.setProductId(p1);
        item1.setBinLocation(bin1);
        item1.setQuantity(new BigDecimal("20.000"));
        item1.setReservedQuantity(new BigDecimal("5.000"));
        stockItemRepository.save(item1);

        StockItem item2 = new StockItem();
        item2.setProductId(p2);
        item2.setBinLocation(bin1);
        item2.setQuantity(new BigDecimal("40.000"));
        item2.setReservedQuantity(new BigDecimal("10.000"));
        stockItemRepository.save(item2);

        BatchAtpRequest request = new BatchAtpRequest(warehouse1.getId(), List.of(p1, p2, 999L));

        mockMvc.perform(post("/api/inventory/stock/atp/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items.length()", is(3)))
                .andExpect(jsonPath("$.data.items[0].atpQuantity", is(15.0)))
                .andExpect(jsonPath("$.data.items[1].atpQuantity", is(30.0)))
                .andExpect(jsonPath("$.data.items[2].atpQuantity", is(0)));
    }
}
