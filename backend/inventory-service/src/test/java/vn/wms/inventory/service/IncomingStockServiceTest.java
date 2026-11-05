package vn.wms.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.inventory.entity.IncomingStock;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.IncomingStockRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncomingStockServiceTest {

    @Mock
    private IncomingStockRepository incomingStockRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private IncomingStockService incomingStockService;

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        warehouse.setId(10L);
        warehouse.setCode("WH-HN");
        warehouse.setName("Kho Tổng Hà Nội");
        warehouse.setActive(true);
    }

    @Test
    @DisplayName("Cập nhật Incoming Stock thành công khi nhận sự kiện PO hợp lệ")
    void processPurchaseOrderCreated_Success() {
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(warehouse));
        when(incomingStockRepository.existsByOrderIdAndProductId(100L, 1L)).thenReturn(false);
        when(incomingStockRepository.existsByOrderIdAndProductId(100L, 2L)).thenReturn(false);
        when(incomingStockRepository.save(any(IncomingStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<OrderLine> lines = List.of(
                new OrderLine(1L, "SKU-MILK", "Sữa tươi TH 1L", new BigDecimal("50"), "hộp", new BigDecimal("35000")),
                new OrderLine(2L, "SKU-BREAD", "Bánh mì sandwich", new BigDecimal("20"), "gói", new BigDecimal("22000"))
        );

        OrderCreatedEvent event = new OrderCreatedEvent(
                100L,
                "PO-20261104-0001",
                OrderType.PURCHASE,
                "APPROVED",
                5L,
                null,
                10L,
                "Kho Tổng Hà Nội",
                new BigDecimal("2190000"),
                "purchasing_user",
                Instant.now(),
                lines
        );

        List<IncomingStock> result = incomingStockService.processPurchaseOrderCreated(event, "msg-001");

        assertThat(result).hasSize(2);
        ArgumentCaptor<IncomingStock> captor = ArgumentCaptor.forClass(IncomingStock.class);
        verify(incomingStockRepository, times(2)).save(captor.capture());

        List<IncomingStock> saved = captor.getAllValues();
        assertThat(saved.get(0).getSkuCode()).isEqualTo("SKU-MILK");
        assertThat(saved.get(0).getQuantity()).isEqualByComparingTo("50");
        assertThat(saved.get(0).getStatus()).isEqualTo("INCOMING");
        assertThat(saved.get(0).getOrderNumber()).isEqualTo("PO-20261104-0001");
        assertThat(saved.get(0).getMessageId()).isEqualTo("msg-001");

        assertThat(saved.get(1).getSkuCode()).isEqualTo("SKU-BREAD");
        assertThat(saved.get(1).getQuantity()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Đảm bảo Idempotency: bỏ qua nếu dòng sản phẩm của PO đã được xử lý trước đó")
    void processPurchaseOrderCreated_Idempotency() {
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(warehouse));
        when(incomingStockRepository.existsByOrderIdAndProductId(100L, 1L)).thenReturn(true);

        List<OrderLine> lines = List.of(
                new OrderLine(1L, "SKU-MILK", "Sữa tươi TH 1L", new BigDecimal("50"), "hộp", new BigDecimal("35000"))
        );

        OrderCreatedEvent event = new OrderCreatedEvent(
                100L,
                "PO-20261104-0001",
                OrderType.PURCHASE,
                "APPROVED",
                5L,
                null,
                10L,
                "Kho Tổng Hà Nội",
                new BigDecimal("1750000"),
                "purchasing_user",
                Instant.now(),
                lines
        );

        List<IncomingStock> result = incomingStockService.processPurchaseOrderCreated(event, "msg-duplicate");

        assertThat(result).isEmpty();
        verify(incomingStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bỏ qua sự kiện không phải đơn mua (SALES)")
    void processPurchaseOrderCreated_IgnoreSalesOrder() {
        OrderCreatedEvent salesEvent = new OrderCreatedEvent(
                200L,
                "SO-20261104-0001",
                OrderType.SALES,
                "APPROVED",
                null,
                8L,
                10L,
                "Kho Tổng Hà Nội",
                new BigDecimal("500000"),
                "sales_user",
                Instant.now(),
                List.of(new OrderLine(1L, "SKU-MILK", "Sữa tươi TH 1L", new BigDecimal("10"), "hộp", new BigDecimal("40000")))
        );

        List<IncomingStock> result = incomingStockService.processPurchaseOrderCreated(salesEvent, "msg-sales");

        assertThat(result).isEmpty();
        verify(warehouseRepository, never()).findById(any());
        verify(incomingStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("Báo lỗi NOT_FOUND khi kho không tồn tại")
    void processPurchaseOrderCreated_WarehouseNotFound() {
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        OrderCreatedEvent event = new OrderCreatedEvent(
                100L,
                "PO-20261104-0001",
                OrderType.PURCHASE,
                "APPROVED",
                5L,
                null,
                999L,
                "Kho Ảo",
                BigDecimal.TEN,
                "purchasing_user",
                Instant.now(),
                List.of(new OrderLine(1L, "SKU-MILK", "Sữa tươi", BigDecimal.ONE, "hộp", BigDecimal.TEN))
        );

        assertThatThrownBy(() -> incomingStockService.processPurchaseOrderCreated(event, "msg-err"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Không tìm thấy kho hàng");
    }

    @Test
    @DisplayName("Tra cứu tổng số lượng incoming stock của SKU tại kho")
    void calculateIncomingQuantity() {
        when(incomingStockRepository.calculateIncomingQuantity(10L, 1L))
                .thenReturn(new BigDecimal("150.000"));

        var response = incomingStockService.getProductIncomingSummary(10L, 1L);

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.warehouseId()).isEqualTo(10L);
        assertThat(response.incomingQuantity()).isEqualByComparingTo("150.000");
    }
}

