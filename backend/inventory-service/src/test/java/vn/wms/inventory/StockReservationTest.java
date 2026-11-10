package vn.wms.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.StockAtpResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.messaging.SoftReservationHandler;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.StockReservationService;
import vn.wms.inventory.service.StockService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockReservationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SoftReservationHandler softReservationHandler;

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private StockService stockService;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private BinLocationRepository binLocationRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    private Warehouse warehouse;
    private BinLocation binLocation;
    private StockItem stockItem;
    private final Long productId = 5001L;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);
        warehouse = new Warehouse();
        warehouse.setCode("WH-RES-01");
        warehouse.setName("Kho Giữ Hàng Tân Bình");
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        binLocation = new BinLocation();
        binLocation.setWarehouse(warehouse);
        binLocation.setCode("WH-RES-01-A-01-01");
        binLocation.setZone("A");
        binLocation.setRack("01");
        binLocation.setBin("01");
        binLocation.setActive(true);
        binLocation = binLocationRepository.save(binLocation);

        // Ban đầu: OnHand = 100, Reserved = 0 -> ATP = 100
        stockItem = new StockItem();
        stockItem.setProductId(productId);
        stockItem.setBinLocation(binLocation);
        stockItem.setQuantity(new BigDecimal("100.000"));
        stockItem.setReservedQuantity(BigDecimal.ZERO);
        stockItem = stockItemRepository.save(stockItem);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Cơ chế giữ hàng ảo (Soft Reservation): Tự động tăng Reserved Stock, giảm ATP khi xác nhận SO (FR16)")
    void testSoftReservationIncreasesReservedAndReducesAtp() {
        // Kiểm tra ban đầu
        StockAtpResponse initialAtp = stockService.getAtp(productId, warehouse.getId());
        assertThat(initialAtp.onHandQuantity()).isEqualByComparingTo("100.000");
        assertThat(initialAtp.reservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(initialAtp.atpQuantity()).isEqualByComparingTo("100.000");

        // Nhận sự kiện order.created cho đơn bán SO-20261104-0001 cần 35 đơn vị
        OrderCreatedEvent event = new OrderCreatedEvent(
                1001L,
                "SO-20261104-0001",
                OrderType.SALES,
                "CONFIRMED",
                null,
                2001L,
                warehouse.getId(),
                warehouse.getName(),
                new BigDecimal("1500000.00"),
                "sales_user",
                Instant.now(),
                List.of(new OrderLine(productId, "SKU-5001", "Bánh Cosy", new BigDecimal("35.000"), "Hộp", new BigDecimal("50000.00")))
        );

        softReservationHandler.onOrderCreated(event, "msg-res-001");

        // Sau khi giữ hàng: Reserved tăng lên 35, ATP giảm xuống còn 65 (100 - 35)
        StockAtpResponse updatedAtp = stockService.getAtp(productId, warehouse.getId());
        assertThat(updatedAtp.onHandQuantity()).isEqualByComparingTo("100.000");
        assertThat(updatedAtp.reservedQuantity()).isEqualByComparingTo("35.000");
        assertThat(updatedAtp.atpQuantity()).isEqualByComparingTo("65.000");

        // Bản ghi giữ chỗ được lưu trong CSDL
        List<StockReservation> reservations = stockReservationRepository.findByOrderIdAndStatus(1001L, "ACTIVE");
        assertThat(reservations).hasSize(1);
        assertThat(reservations.get(0).getReservedQuantity()).isEqualByComparingTo("35.000");
        assertThat(reservations.get(0).getOrderNumber()).isEqualTo("SO-20261104-0001");
    }

    @Test
    @DisplayName("Chống giữ hàng trùng lặp (Idempotent) khi cùng một messageId hoặc orderId được gửi lại")
    void testSoftReservationIsIdempotent() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                1002L,
                "SO-20261104-0002",
                OrderType.SALES,
                "CONFIRMED",
                null,
                2001L,
                warehouse.getId(),
                warehouse.getName(),
                new BigDecimal("1000000.00"),
                "sales_user",
                Instant.now(),
                List.of(new OrderLine(productId, "SKU-5001", "Bánh Cosy", new BigDecimal("20.000"), "Hộp", new BigDecimal("50000.00")))
        );

        // Giao tin nhắn lần 1
        softReservationHandler.onOrderCreated(event, "msg-res-002");
        StockAtpResponse atp1 = stockService.getAtp(productId, warehouse.getId());
        assertThat(atp1.reservedQuantity()).isEqualByComparingTo("20.000");

        // Giao tin nhắn lần 2 (trùng messageId)
        softReservationHandler.onOrderCreated(event, "msg-res-002");
        StockAtpResponse atp2 = stockService.getAtp(productId, warehouse.getId());
        assertThat(atp2.reservedQuantity()).isEqualByComparingTo("20.000"); // Không bị cộng dồn thành 40
    }

    @Test
    @DisplayName("Giải phóng giữ hàng ảo khi đơn bán bị hủy: Reserved giảm, ATP phục hồi")
    void testReleaseReservationWhenOrderCancelled() throws Exception {
        // Giữ hàng 40 đơn vị
        OrderCreatedEvent event = new OrderCreatedEvent(
                1003L,
                "SO-20261104-0003",
                OrderType.SALES,
                "CONFIRMED",
                null,
                2001L,
                warehouse.getId(),
                warehouse.getName(),
                new BigDecimal("2000000.00"),
                "sales_user",
                Instant.now(),
                List.of(new OrderLine(productId, "SKU-5001", "Bánh Cosy", new BigDecimal("40.000"), "Hộp", new BigDecimal("50000.00")))
        );

        softReservationHandler.onOrderCreated(event, "msg-res-003");
        assertThat(stockService.getAtp(productId, warehouse.getId()).atpQuantity()).isEqualByComparingTo("60.000");

        // Gọi API giải phóng giữ chỗ khi đơn 1003 bị hủy
        mockMvc.perform(post("/api/inventory/stock/reservations/release")
                        .param("orderId", "1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // ATP phục hồi về 100, Reserved về 0
        StockAtpResponse restoredAtp = stockService.getAtp(productId, warehouse.getId());
        assertThat(restoredAtp.reservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(restoredAtp.atpQuantity()).isEqualByComparingTo("100.000");

        // Bản ghi reservation chuyển sang trạng thái RELEASED
        List<StockReservation> active = stockReservationRepository.findByOrderIdAndStatus(1003L, "ACTIVE");
        assertThat(active).isEmpty();
    }

    @Test
    @DisplayName("Bỏ qua giữ hàng khi sự kiện đơn hàng không phải loại SALES (ví dụ PURCHASE)")
    void testIgnoreNonSalesOrder() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                2001L,
                "PO-20261104-0001",
                OrderType.PURCHASE,
                "APPROVED",
                101L,
                null,
                warehouse.getId(),
                warehouse.getName(),
                new BigDecimal("5000000.00"),
                "purchasing_user",
                Instant.now(),
                List.of(new OrderLine(productId, "SKU-5001", "Bánh Cosy", new BigDecimal("50.000"), "Hộp", new BigDecimal("50000.00")))
        );

        softReservationHandler.onOrderCreated(event, "msg-po-001");

        // Reserved không đổi, vẫn là 0
        StockAtpResponse atp = stockService.getAtp(productId, warehouse.getId());
        assertThat(atp.reservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(atp.atpQuantity()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("Giữ hàng ảo tự động bỏ qua các vị trí/lô bị khóa hoặc đã hết hạn sử dụng")
    void testReservationSkipsLockedAndExpiredLots() {
        Long pId = 5002L;
        java.time.LocalDate today = java.time.LocalDate.now();

        // Ô 1: Lô bị khóa
        StockItem locked = new StockItem();
        locked.setProductId(pId);
        locked.setBinLocation(binLocation);
        locked.setLotNo("LOT-LOCKED-01");
        locked.setQuantity(new BigDecimal("50.000"));
        locked.setReservedQuantity(BigDecimal.ZERO);
        locked.setLocked(true);
        stockItemRepository.save(locked);

        // Ô 2: Lô hết hạn
        StockItem expired = new StockItem();
        expired.setProductId(pId);
        expired.setBinLocation(binLocation);
        expired.setLotNo("LOT-EXPIRED-01");
        expired.setQuantity(new BigDecimal("40.000"));
        expired.setReservedQuantity(BigDecimal.ZERO);
        expired.setExpDate(today.minusDays(2));
        stockItemRepository.save(expired);

        // Tạo đơn bán yêu cầu 20 cái
        OrderCreatedEvent event = new OrderCreatedEvent(
                3001L,
                "SO-2026-SKIP-01",
                OrderType.SALES,
                "CONFIRMED",
                null,
                201L,
                warehouse.getId(),
                warehouse.getName(),
                new BigDecimal("2000000.00"),
                "sales_user",
                Instant.now(),
                List.of(new OrderLine(pId, "SKU-5002", "Bánh Quy", new BigDecimal("20.000"), "Hộp", new BigDecimal("100000.00")))
        );

        softReservationHandler.onOrderCreated(event, "msg-skip-001");

        // Không có bản ghi giữ chỗ nào được tạo vì cả 2 lô đều không khả dụng (bị khóa hoặc hết hạn)
        List<StockReservation> reservations = stockReservationRepository.findByOrderIdAndStatus(3001L, "ACTIVE");
        assertThat(reservations).isEmpty();
    }
}
