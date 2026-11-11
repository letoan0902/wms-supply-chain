package vn.wms.inventory.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.client.ProductClient;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockLedgerStart;
import vn.wms.inventory.entity.StockMovement;
import vn.wms.inventory.entity.StockMovementType;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockDispatchRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockLedgerStartRepository;
import vn.wms.inventory.repository.StockMovementRepository;
import vn.wms.inventory.repository.StockReceiptRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.StockTransferItemRepository;
import vn.wms.inventory.repository.StockTransferRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.StockDeductionService;
import vn.wms.inventory.service.StockReceiptService;
import vn.wms.inventory.service.StockTransferService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Nền chung cho test báo cáo kho (WMS-17.1, WMS-17.2): dựng kho, vị trí, chạy các luồng nghiệp vụ thật (nhập GRN,
 * xuất bán, điều chuyển) để sổ kho được ghi đúng như khi chạy thật, rồi gọi API báo cáo bằng MockMvc.
 * Không đặt {@code @Transactional}: mỗi lời gọi mở transaction riêng như request thật, dữ liệu được dọn trước và sau.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ReportTestSupport {

    static final Long COMPANY_A = 1L;
    static final Long COMPANY_B = 2L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    WarehouseRepository warehouseRepository;
    @Autowired
    BinLocationRepository binLocationRepository;
    @Autowired
    StockItemRepository stockItemRepository;
    @Autowired
    InventoryLotRepository inventoryLotRepository;
    @Autowired
    StockReservationRepository stockReservationRepository;
    @Autowired
    StockTransferRepository stockTransferRepository;
    @Autowired
    StockTransferItemRepository stockTransferItemRepository;
    @Autowired
    StockReceiptRepository stockReceiptRepository;
    @Autowired
    StockDispatchRepository stockDispatchRepository;
    @Autowired
    StockMovementRepository stockMovementRepository;
    @Autowired
    StockLedgerStartRepository stockLedgerStartRepository;
    @Autowired
    StockReceiptService stockReceiptService;
    @Autowired
    StockDeductionService stockDeductionService;
    @Autowired
    StockTransferService stockTransferService;

    /** product-service không chạy trong test: giả lập để kiểm tra bộ lọc danh mục, từ khóa. */
    @MockBean
    ProductClient productClient;

    @BeforeEach
    void cleanBefore() {
        cleanUp();
    }

    @AfterEach
    void cleanAfter() {
        cleanUp();
        TenantContext.clear();
    }

    private void cleanUp() {
        for (Long company : List.of(COMPANY_A, COMPANY_B)) {
            TenantContext.runAs(company, () -> {
                stockMovementRepository.deleteAll();
                stockLedgerStartRepository.deleteAll();
                stockTransferItemRepository.deleteAll();
                stockTransferRepository.deleteAll();
                stockReservationRepository.deleteAll();
                stockReceiptRepository.deleteAll();
                stockDispatchRepository.deleteAll();
                inventoryLotRepository.deleteAll();
                stockItemRepository.deleteAll();
                binLocationRepository.deleteAll();
                warehouseRepository.deleteAll();
            });
        }
    }

    Warehouse warehouse(Long company, String code, String name) {
        return TenantContext.callAs(company, () ->
                warehouseRepository.save(new Warehouse(code, name, "Địa chỉ " + name, "Quản lý", "0900000000", 100)));
    }

    BinLocation bin(Long company, Warehouse warehouse, String zone) {
        return TenantContext.callAs(company, () ->
                binLocationRepository.save(new BinLocation(warehouse, zone, "R01", "B01", new BigDecimal("1000.00"))));
    }

    /** Nhập kho qua đúng service xử lý sự kiện order.received. */
    void receive(Long company, Long grnId, String grnNumber, Long poId, String poNumber, Long supplierId,
                 Warehouse warehouse, BinLocation bin, Long productId, String sku, String lotNo, String quantity) {
        ReceivedItemLine line = new ReceivedItemLine(productId, sku, "Sản phẩm " + sku, new BigDecimal(quantity),
                BigDecimal.ZERO, "Thùng", bin.getId(), bin.getCode(), lotNo, LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 10, 1));
        OrderReceivedEvent event = new OrderReceivedEvent(grnId, grnNumber, poId, poNumber, warehouse.getId(),
                supplierId, "thukho01", LocalDate.now(), Instant.now(), List.of(line));
        TenantContext.runAs(company, () -> stockReceiptService.processOrderReceived(event, "msg-" + grnNumber));
    }

    /** Xuất bán qua đúng service trừ tồn của sự kiện order.dispatched. */
    void dispatch(Long company, Long orderId, String orderNumber, Warehouse warehouse, BinLocation bin,
                  Long productId, String sku, String quantity) {
        DeductStockRequest request = new DeductStockRequest(orderId, orderNumber, warehouse.getId(), "SALES_DISPATCH",
                "Xuất giao khách", List.of(new DeductStockItemRequest(productId, sku, new BigDecimal(quantity),
                bin != null ? bin.getId() : null, null)));
        TenantContext.runAs(company, () -> stockDeductionService.deductStock(request, "thukho02"));
    }

    /** Ghi thẳng một dòng sổ với thời điểm tùy chọn, dùng cho test biên ngày và phân trang. */
    StockMovement movement(Long company, Warehouse warehouse, Long productId, StockMovementType type,
                           String quantity, Instant occurredAt) {
        StockMovement m = new StockMovement();
        m.setOccurredAt(occurredAt);
        m.setMovementType(type);
        m.setWarehouseId(warehouse.getId());
        m.setProductId(productId);
        m.setSkuCode("SKU-" + productId);
        m.setProductName("Sản phẩm " + productId);
        m.setQuantityIn(type.isInbound() ? new BigDecimal(quantity) : BigDecimal.ZERO);
        m.setQuantityOut(type.isInbound() ? BigDecimal.ZERO : new BigDecimal(quantity));
        m.setDocumentType(StockMovement.DOC_OTHER);
        m.setDocumentNumber("DOC-" + type + "-" + occurredAt.toEpochMilli());
        return TenantContext.callAs(company, () -> stockMovementRepository.save(m));
    }

    void ledgerStart(Long company, Instant startedAt) {
        StockLedgerStart start = new StockLedgerStart();
        start.setStartedAt(startedAt);
        TenantContext.runAs(company, () -> stockLedgerStartRepository.save(start));
    }

    MockHttpServletRequestBuilder asAdmin(String url, Long company) {
        return get(url).header("X-Company-Id", company).header("X-User-Name", "admin").header("X-User-Roles", "ADMIN");
    }

    MockHttpServletRequestBuilder asRole(String url, Long company, String role, Long... warehouseIds) {
        StringBuilder ids = new StringBuilder();
        for (Long id : warehouseIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(id);
        }
        return get(url).header("X-Company-Id", company).header("X-User-Name", "user-" + role)
                .header("X-User-Roles", role).header("X-User-Warehouse-Ids", ids.toString());
    }

    JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    static BigDecimal dec(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.decimalValue();
    }
}
