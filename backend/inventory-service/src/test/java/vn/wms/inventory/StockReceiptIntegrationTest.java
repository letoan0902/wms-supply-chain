package vn.wms.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.IncomingStock;
import vn.wms.inventory.entity.InventoryLot;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.IncomingStockRepository;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.StockReceiptService;
import vn.wms.inventory.service.StockService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockReceiptIntegrationTest {

    @Autowired
    private StockReceiptService stockReceiptService;

    @Autowired
    private StockService stockService;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private BinLocationRepository binLocationRepository;

    @Autowired
    private IncomingStockRepository incomingStockRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private InventoryLotRepository inventoryLotRepository;

    private Warehouse warehouse;
    private BinLocation binLocation;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        warehouse.setCode("WH-RECEIPT-01");
        warehouse.setName("Kho Nhập Hàng Test");
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        binLocation = new BinLocation();
        binLocation.setWarehouse(warehouse);
        binLocation.setZone("ZONE-A");
        binLocation.setRack("01");
        binLocation.setBin("01");
        binLocation.setCode("WH-RECEIPT-01-A-01-01");
        binLocation.setActive(true);
        binLocation.setStatus("AVAILABLE");
        binLocation = binLocationRepository.save(binLocation);
    }

    @Test
    @DisplayName("Khi xác nhận GRN: tăng OnHand Stock, giảm Incoming Stock, lưu Lô & HSD (WMS-10.3 - FR11, FR13)")
    void testStockReceipt_IncreasesOnHandAndReducesIncoming() {
        Long productId = 101L;
        Long poId = 501L;

        // Giả lập trước đó PO đã được duyệt -> tạo Incoming Stock 100 cái
        IncomingStock incoming = IncomingStock.builder()
                .warehouse(warehouse)
                .productId(productId)
                .skuCode("SKU-COCA-01")
                .productName("Coca Cola 330ml")
                .orderId(poId)
                .orderNumber("PO-20261108-0001")
                .quantity(new BigDecimal("100.000"))
                .uom("lon")
                .status("INCOMING")
                .build();
        incomingStockRepository.save(incoming);

        // Nhận hàng 100 cái đạt chuẩn vào kho
        ReceivedItemLine line = new ReceivedItemLine(
                productId, "SKU-COCA-01", "Coca Cola 330ml",
                new BigDecimal("100.000"), BigDecimal.ZERO, "lon",
                binLocation.getId(), binLocation.getCode(),
                "LOT-COCA-2026", LocalDate.now(), LocalDate.now().plusMonths(12)
        );

        OrderReceivedEvent event = new OrderReceivedEvent(
                1L, "GRN-20261108-0001", poId, "PO-20261108-0001",
                warehouse.getId(), 10L, "wh_staff",
                LocalDate.now(), Instant.now(), List.of(line)
        );

        stockReceiptService.processOrderReceived(event, "msg-rec-test-1");

        // 1. Kiểm tra Incoming Stock đã giảm về 0 và chuyển trạng thái RECEIVED
        IncomingStock updatedIncoming = incomingStockRepository.findByOrderIdAndProductId(poId, productId).orElseThrow();
        assertThat(updatedIncoming.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updatedIncoming.getStatus()).isEqualTo("RECEIVED");

        // 2. Kiểm tra StockItem đã được tăng OnHand = 100
        List<StockItem> stockItems = stockItemRepository.findByBinLocationId(binLocation.getId());
        assertThat(stockItems).hasSize(1);
        StockItem stockItem = stockItems.get(0);
        assertThat(stockItem.getProductId()).isEqualTo(productId);
        assertThat(stockItem.getQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(stockItem.getLotNo()).isEqualTo("LOT-COCA-2026");
        assertThat(stockItem.getExpDate()).isEqualTo(LocalDate.now().plusMonths(12));

        // 3. Kiểm tra InventoryLot đã được lưu chi tiết
        InventoryLot lot = inventoryLotRepository
                .findByLotNumberAndBinLocationIdAndProductId("LOT-COCA-2026", binLocation.getId(), productId)
                .orElseThrow();
        assertThat(lot.getQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(lot.isLocked()).isFalse();

        // 4. Kiểm tra ATP tăng tương ứng
        StockAtpResponse atp = stockService.getAtp(productId, warehouse.getId());
        assertThat(atp.onHandQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(atp.atpQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));
    }

    @Test
    @DisplayName("Nhập kho từng phần (Partial Receipt): Giảm Incoming tương ứng số lượng nhận, giữ trạng thái INCOMING khi chưa hết")
    void testPartialStockReceipt_UpdatesQuantitiesCorrectly() {
        Long productId = 102L;
        Long poId = 502L;

        // PO đặt 100 đơn vị
        IncomingStock incoming = IncomingStock.builder()
                .warehouse(warehouse)
                .productId(productId)
                .skuCode("SKU-PEPSI-01")
                .productName("Pepsi 330ml")
                .orderId(poId)
                .orderNumber("PO-20261108-0002")
                .quantity(new BigDecimal("100.000"))
                .uom("lon")
                .status("INCOMING")
                .build();
        incomingStockRepository.save(incoming);

        // Đợt 1: Nhận trước 40 đơn vị
        ReceivedItemLine line1 = new ReceivedItemLine(
                productId, "SKU-PEPSI-01", "Pepsi 330ml",
                new BigDecimal("40.000"), BigDecimal.ZERO, "lon",
                binLocation.getId(), binLocation.getCode(),
                "LOT-PEPSI-DOT1", LocalDate.now(), LocalDate.now().plusMonths(6)
        );
        OrderReceivedEvent event1 = new OrderReceivedEvent(
                2L, "GRN-20261108-0002", poId, "PO-20261108-0002",
                warehouse.getId(), 10L, "wh_staff",
                LocalDate.now(), Instant.now(), List.of(line1)
        );

        stockReceiptService.processOrderReceived(event1, "msg-partial-1");

        IncomingStock incomingAfterBatch1 = incomingStockRepository.findByOrderIdAndProductId(poId, productId).orElseThrow();
        assertThat(incomingAfterBatch1.getQuantity()).isEqualByComparingTo(new BigDecimal("60.000"));
        assertThat(incomingAfterBatch1.getStatus()).isEqualTo("INCOMING");

        // Đợt 2: Nhận nốt 60 đơn vị còn lại
        ReceivedItemLine line2 = new ReceivedItemLine(
                productId, "SKU-PEPSI-01", "Pepsi 330ml",
                new BigDecimal("60.000"), BigDecimal.ZERO, "lon",
                binLocation.getId(), binLocation.getCode(),
                "LOT-PEPSI-DOT2", LocalDate.now(), LocalDate.now().plusMonths(8)
        );
        OrderReceivedEvent event2 = new OrderReceivedEvent(
                3L, "GRN-20261108-0003", poId, "PO-20261108-0002",
                warehouse.getId(), 10L, "wh_staff",
                LocalDate.now(), Instant.now(), List.of(line2)
        );

        stockReceiptService.processOrderReceived(event2, "msg-partial-2");

        IncomingStock incomingAfterBatch2 = incomingStockRepository.findByOrderIdAndProductId(poId, productId).orElseThrow();
        assertThat(incomingAfterBatch2.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(incomingAfterBatch2.getStatus()).isEqualTo("RECEIVED");

        // Tổng OnHand tại kho phải là 40 + 60 = 100
        StockAtpResponse atp = stockService.getAtp(productId, warehouse.getId());
        assertThat(atp.onHandQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));
    }
}
