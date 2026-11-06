package vn.wms.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.messaging.DispatchStockHandler;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.WarehouseRepository;
import vn.wms.inventory.service.StockDeductionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class StockPessimisticLockTest {

    @Autowired
    private StockDeductionService stockDeductionService;

    @Autowired
    private DispatchStockHandler dispatchStockHandler;

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
    private final Long testProductId = 8801L;

    @BeforeEach
    void setUp() {
        stockReservationRepository.deleteAll();
        stockItemRepository.deleteAll();
        binLocationRepository.deleteAll();
        warehouseRepository.deleteAll();

        warehouse = new Warehouse();
        warehouse.setCode("WH-PESSIMISTIC-01");
        warehouse.setName("Kho Thử Nghiệm Khóa Bi Quan");
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        binLocation = new BinLocation();
        binLocation.setWarehouse(warehouse);
        binLocation.setCode("WH-PESS-01-A-01-01");
        binLocation.setZone("A");
        binLocation.setRack("01");
        binLocation.setBin("01");
        binLocation.setActive(true);
        binLocation = binLocationRepository.save(binLocation);
    }

    @Test
    @DisplayName("Trừ tồn kho thành công với Khóa bi quan khi tồn kho hợp lệ")
    void testDeductStockSuccess() {
        StockItem stockItem = new StockItem();
        stockItem.setProductId(testProductId);
        stockItem.setBinLocation(binLocation);
        stockItem.setQuantity(new BigDecimal("100.000"));
        stockItem.setReservedQuantity(BigDecimal.ZERO);
        stockItem = stockItemRepository.save(stockItem);

        DeductStockRequest request = new DeductStockRequest(
                1001L,
                "SO-20261106-0001",
                warehouse.getId(),
                "SALES_DISPATCH",
                "Xuất kho đơn bán hàng",
                List.of(new DeductStockItemRequest(testProductId, "SKU-TEST-01", new BigDecimal("35.000"), binLocation.getId(), null))
        );

        DeductStockResponse response = stockDeductionService.deductStock(request, "tester");

        assertThat(response.totalDeductedQuantity()).isEqualByComparingTo(new BigDecimal("35.000"));
        assertThat(response.deductedItems()).hasSize(1);
        assertThat(response.deductedItems().get(0).remainingQuantity()).isEqualByComparingTo(new BigDecimal("65.000"));

        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualByComparingTo(new BigDecimal("65.000"));
    }

    @Test
    @DisplayName("Chống âm kho tuyệt đối: Từ chối xuất và rollback khi tồn kho thực tế không đủ (FR17, NFR03)")
    void testAntiNegativeStock_ThrowsExceptionWhenInsufficient() {
        StockItem stockItem = new StockItem();
        stockItem.setProductId(testProductId);
        stockItem.setBinLocation(binLocation);
        stockItem.setQuantity(new BigDecimal("10.000"));
        stockItem.setReservedQuantity(BigDecimal.ZERO);
        stockItem = stockItemRepository.save(stockItem);

        // Yêu cầu trừ 15.000 trong khi chỉ có 10.000
        DeductStockRequest request = new DeductStockRequest(
                1002L,
                "SO-20261106-0002",
                warehouse.getId(),
                "SALES_DISPATCH",
                "Xuất kho vượt quá tồn",
                List.of(new DeductStockItemRequest(testProductId, "SKU-TEST-01", new BigDecimal("15.000"), binLocation.getId(), null))
        );

        assertThatThrownBy(() -> stockDeductionService.deductStock(request, "tester"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Không đủ tồn kho để xuất");

        // Đảm bảo số lượng tồn ban đầu vẫn là 10.000, tuyệt đối không bị âm
        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualByComparingTo(new BigDecimal("10.000"));
    }

    @Test
    @DisplayName("Xuất kho đơn hàng có giữ hàng ảo (Soft Reservation): Giảm cả OnHand và Reserved, đổi trạng thái FULFILLED")
    void testDeductStockWithSoftReservation() {
        StockItem stockItem = new StockItem();
        stockItem.setProductId(testProductId);
        stockItem.setBinLocation(binLocation);
        stockItem.setQuantity(new BigDecimal("50.000"));
        stockItem.setReservedQuantity(new BigDecimal("20.000"));
        stockItem = stockItemRepository.save(stockItem);

        Long orderId = 2001L;
        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setOrderNumber("SO-20261106-2001");
        reservation.setProductId(testProductId);
        reservation.setStockItem(stockItem);
        reservation.setWarehouseId(warehouse.getId());
        reservation.setReservedQuantity(new BigDecimal("20.000"));
        reservation.setStatus("ACTIVE");
        stockReservationRepository.save(reservation);

        DeductStockRequest request = new DeductStockRequest(
                orderId,
                "SO-20261106-2001",
                warehouse.getId(),
                "SALES_DISPATCH",
                "Xuất đơn hàng đã giữ chỗ",
                List.of(new DeductStockItemRequest(testProductId, "SKU-TEST-01", new BigDecimal("20.000"), null, null))
        );

        DeductStockResponse response = stockDeductionService.deductStock(request, "operator");

        assertThat(response.totalDeductedQuantity()).isEqualByComparingTo(new BigDecimal("20.000"));

        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        // OnHand giảm từ 50 xuống 30
        assertThat(reloaded.getQuantity()).isEqualByComparingTo(new BigDecimal("30.000"));
        // Reserved giảm từ 20 xuống 0
        assertThat(reloaded.getReservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        StockReservation updatedRes = stockReservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updatedRes.getStatus()).isEqualTo("FULFILLED");
    }

    @Test
    @DisplayName("Khóa bi quan đồng thời (Pessimistic Concurrency): 2 luồng tranh chấp cùng trừ hàng, chỉ 1 luồng thành công và không bị âm kho")
    void testConcurrentDeduction_PessimisticLockPreventsNegativeStock() throws InterruptedException {
        // Khởi tạo tồn kho ban đầu = 10.000
        StockItem stockItem = new StockItem();
        stockItem.setProductId(testProductId);
        stockItem.setBinLocation(binLocation);
        stockItem.setQuantity(new BigDecimal("10.000"));
        stockItem.setReservedQuantity(BigDecimal.ZERO);
        stockItem = stockItemRepository.save(stockItem);

        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errorMessages = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            final long orderId = 3000L + i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Đợi cả 2 luồng cùng xuất phát đồng thời
                    DeductStockRequest request = new DeductStockRequest(
                            orderId,
                            "SO-RACE-" + orderId,
                            warehouse.getId(),
                            "SALES_DISPATCH",
                            "Tranh chấp xuất kho đồng thời",
                            List.of(new DeductStockItemRequest(testProductId, "SKU-TEST-01", new BigDecimal("7.000"), binLocation.getId(), null))
                    );
                    stockDeductionService.deductStock(request, "thread-" + orderId);
                    successCount.incrementAndGet();
                } catch (ApiException e) {
                    failCount.incrementAndGet();
                    errorMessages.add(e.getMessage());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errorMessages.add(e.toString());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Kích hoạt 2 luồng chạy cùng lúc
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();

        // Vì ban đầu có 10, mỗi luồng đòi trừ 7 (tổng = 14 > 10)
        // Nhờ Khóa bi quan PESSIMISTIC_WRITE: Đúng 1 luồng thành công (trừ 7 còn 3), luồng kia bị từ chối
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        assertThat(errorMessages.get(0)).contains("Không đủ tồn kho để xuất");

        // Kiểm tra tồn kho cuối cùng trong Database: Phải đúng là 3.000, TUYỆT ĐỐI KHÔNG BỊ ÂM
        StockItem finalStock = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(finalStock.getQuantity()).isEqualByComparingTo(new BigDecimal("3.000"));
    }

    @Test
    @DisplayName("Handler sự kiện order.dispatched: Tự động trừ tồn kho khi nhận thông điệp từ Message Broker")
    void testDispatchStockHandler_OnOrderDispatched() {
        StockItem stockItem = new StockItem();
        stockItem.setProductId(testProductId);
        stockItem.setBinLocation(binLocation);
        stockItem.setQuantity(new BigDecimal("80.000"));
        stockItem.setReservedQuantity(BigDecimal.ZERO);
        stockItem = stockItemRepository.save(stockItem);

        OrderDispatchedEvent event = new OrderDispatchedEvent(
                4001L,
                "SO-20261106-4001",
                OrderType.SALES,
                warehouse.getId(),
                "warehouse_keeper",
                Instant.now(),
                List.of(new OrderLine(testProductId, "SKU-TEST-01", "Sản phẩm test", new BigDecimal("25.000"), "Hộp", BigDecimal.valueOf(100000)))
        );

        dispatchStockHandler.onOrderDispatched(event, "msg-dispatch-001");

        StockItem reloaded = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualByComparingTo(new BigDecimal("55.000"));
    }
}
