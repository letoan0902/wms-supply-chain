package vn.wms.inventory.stocktake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.IncomingStockRepository;
import vn.wms.inventory.repository.StockAdjustmentRepository;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReceiptRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.StockTransferItemRepository;
import vn.wms.inventory.repository.StockTransferRepository;
import vn.wms.inventory.repository.StocktakeLineRepository;
import vn.wms.inventory.repository.StocktakeLocationRepository;
import vn.wms.inventory.repository.StocktakeSessionRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dữ liệu và tiện ích dùng chung cho các test kiểm kê: hai kho của doanh nghiệp A (kho 1 có ba vị trí, kho 2 có một),
 * một kho của doanh nghiệp B, và các tài khoản theo vai trò gửi qua header như api-gateway.
 */
abstract class StocktakeTestSupport {

    protected static final Long COMPANY_A = 1L;
    protected static final Long COMPANY_B = 2L;
    protected static final Long PRODUCT_1 = 9101L;
    protected static final Long PRODUCT_2 = 9102L;

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected WarehouseRepository warehouseRepository;
    @Autowired
    protected BinLocationRepository binLocationRepository;
    @Autowired
    protected StockItemRepository stockItemRepository;
    @Autowired
    protected InventoryLotRepository inventoryLotRepository;
    @Autowired
    protected StockReservationRepository stockReservationRepository;
    @Autowired
    protected StockReceiptRepository stockReceiptRepository;
    @Autowired
    protected IncomingStockRepository incomingStockRepository;
    @Autowired
    protected StockTransferRepository stockTransferRepository;
    @Autowired
    protected StockTransferItemRepository stockTransferItemRepository;
    @Autowired
    protected StocktakeSessionRepository sessionRepository;
    @Autowired
    protected StocktakeLocationRepository locationRepository;
    @Autowired
    protected StocktakeLineRepository lineRepository;
    @Autowired
    protected StockAdjustmentRepository adjustmentRepository;

    protected Warehouse warehouse1;
    protected Warehouse warehouse2;
    protected Warehouse warehouseB;
    protected BinLocation binA1;
    protected BinLocation binA2;
    protected BinLocation binB1;
    protected BinLocation binW2;
    protected BinLocation binOfCompanyB;

    /** Người dùng giả lập: tên, vai trò (phân tách dấu phẩy), danh sách kho (phân tách dấu phẩy). */
    protected record TestUser(String name, String roles, String warehouseIds) {
    }

    protected TestUser admin;
    protected TestUser manager1;
    protected TestUser manager2;
    protected TestUser staff1;
    protected TestUser accountant;
    protected TestUser adminB;

    @BeforeEach
    void setUpStocktakeData() {
        cleanAll();
        TenantContext.runAs(COMPANY_A, () -> {
            warehouse1 = warehouseRepository.save(new Warehouse("WH-ST-01", "Kho kiểm kê 1", "Hà Nội", "QL 1", "0901", 500));
            warehouse2 = warehouseRepository.save(new Warehouse("WH-ST-02", "Kho kiểm kê 2", "Hải Phòng", "QL 2", "0902", 500));
            binA1 = binLocationRepository.save(new BinLocation(warehouse1, "ZA", "R01", "B01", new BigDecimal("1000.00")));
            binA2 = binLocationRepository.save(new BinLocation(warehouse1, "ZA", "R01", "B02", new BigDecimal("1000.00")));
            binB1 = binLocationRepository.save(new BinLocation(warehouse1, "ZB", "R01", "B01", new BigDecimal("1000.00")));
            binW2 = binLocationRepository.save(new BinLocation(warehouse2, "ZA", "R01", "B01", new BigDecimal("1000.00")));
        });
        TenantContext.runAs(COMPANY_B, () -> {
            warehouseB = warehouseRepository.save(new Warehouse("WH-ST-01", "Kho B", "Đà Nẵng", "QL B", "0903", 500));
            binOfCompanyB = binLocationRepository.save(new BinLocation(warehouseB, "ZA", "R01", "B01", new BigDecimal("1000.00")));
        });
        admin = new TestUser("admin01", "ADMIN", "");
        manager1 = new TestUser("manager01", "WAREHOUSE_MANAGER", String.valueOf(warehouse1.getId()));
        manager2 = new TestUser("manager02", "WAREHOUSE_MANAGER", String.valueOf(warehouse2.getId()));
        staff1 = new TestUser("staff01", "WAREHOUSE_STAFF", String.valueOf(warehouse1.getId()));
        accountant = new TestUser("acc01", "ACCOUNTANT", "");
        adminB = new TestUser("adminB", "ADMIN", "");
    }

    @AfterEach
    void tearDownStocktakeData() {
        cleanAll();
        TenantContext.clear();
    }

    private void cleanAll() {
        for (Long company : List.of(COMPANY_A, COMPANY_B)) {
            TenantContext.runAs(company, () -> {
                adjustmentRepository.deleteAll();
                lineRepository.deleteAll();
                locationRepository.deleteAll();
                sessionRepository.deleteAll();
                stockTransferItemRepository.deleteAll();
                stockTransferRepository.deleteAll();
                stockReservationRepository.deleteAll();
                stockReceiptRepository.deleteAll();
                incomingStockRepository.deleteAll();
                inventoryLotRepository.deleteAll();
                stockItemRepository.deleteAll();
                binLocationRepository.deleteAll();
                warehouseRepository.deleteAll();
            });
        }
    }

    // ------------------------------------------------------------------------------------------
    // Dữ liệu tồn kho
    // ------------------------------------------------------------------------------------------

    protected StockItem stock(Long company, BinLocation bin, Long productId, String lotNo, String quantity, String reserved) {
        return TenantContext.callAs(company, () -> {
            StockItem item = new StockItem();
            item.setProductId(productId);
            item.setBinLocation(bin);
            item.setLotNo(lotNo);
            item.setQuantity(new BigDecimal(quantity));
            item.setReservedQuantity(new BigDecimal(reserved));
            StockItem saved = stockItemRepository.save(item);
            if (lotNo != null) {
                InventoryLot lot = new InventoryLot();
                lot.setLotNumber(lotNo);
                lot.setProductId(productId);
                lot.setBinLocation(bin);
                lot.setQuantity(new BigDecimal(quantity));
                lot.setReservedQuantity(new BigDecimal(reserved));
                inventoryLotRepository.save(lot);
            }
            return saved;
        });
    }

    protected BigDecimal onHand(Long company, Long stockItemId) {
        return TenantContext.callAs(company, () -> stockItemRepository.findById(stockItemId).orElseThrow().getQuantity());
    }

    // ------------------------------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------------------------------

    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Long company, TestUser user) {
        builder.header("X-Company-Id", company);
        if (user != null) {
            builder.header("X-User-Name", user.name())
                    .header("X-User-Roles", user.roles())
                    .header("X-User-Warehouse-Ids", user.warehouseIds());
        }
        return builder;
    }

    protected ResultActions postJson(String url, Object body, Long company, TestUser user) throws Exception {
        MockHttpServletRequestBuilder builder = post(url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(as(builder, company, user));
    }

    protected ResultActions getAs(String url, Long company, TestUser user) throws Exception {
        return mockMvc.perform(as(get(url), company, user));
    }

    protected JsonNode data(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    protected Map<String, Object> warehouseScope(Long warehouseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", warehouseId);
        body.put("scopeType", "WAREHOUSE");
        body.put("plannedDate", "2026-11-12");
        body.put("notes", "Kiểm kê định kỳ");
        return body;
    }

    protected Map<String, Object> locationScope(Long warehouseId, List<Long> binIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", warehouseId);
        body.put("scopeType", "LOCATIONS");
        body.put("binLocationIds", binIds);
        body.put("plannedDate", "2026-11-12");
        return body;
    }

    /** Lập và bắt đầu một đợt kiểm kê, trả về ID đợt. */
    protected long createAndStart(Map<String, Object> body, Long company, TestUser user) throws Exception {
        long id = data(postJson("/api/inventory/stocktakes", body, company, user)
                .andExpect(status().isCreated())).path("id").asLong();
        postJson("/api/inventory/stocktakes/" + id + "/start", null, company, user).andExpect(status().isOk());
        return id;
    }

    protected Map<String, Object> countBody(Long binId, Long productId, String lotNo, String quantity, String mode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("binLocationId", binId);
        body.put("productId", productId);
        body.put("lotNo", lotNo);
        body.put("quantity", new BigDecimal(quantity));
        body.put("mode", mode);
        return body;
    }
}
