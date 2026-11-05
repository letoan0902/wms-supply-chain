package vn.wms.inventory.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.inventory.service.IncomingStockService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IncomingStockHandlerTest {

    @Mock
    private IncomingStockService incomingStockService;

    @InjectMocks
    private IncomingStockHandler handler;

    @Test
    @DisplayName("Chuyển tiếp sự kiện đơn mua PURCHASE sang IncomingStockService")
    void onOrderCreated_Purchase() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                1L, "PO-001", OrderType.PURCHASE, "APPROVED", 10L, null, 2L, "Kho 1",
                BigDecimal.valueOf(100), "user", Instant.now(),
                List.of(new OrderLine(1L, "SKU-1", "SP 1", BigDecimal.TEN, "cái", BigDecimal.TEN))
        );

        handler.onOrderCreated(event, "msg-123");

        verify(incomingStockService).processPurchaseOrderCreated(eq(event), eq("msg-123"));
    }

    @Test
    @DisplayName("Bỏ qua sự kiện đơn bán SALES")
    void onOrderCreated_Sales() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                2L, "SO-001", OrderType.SALES, "APPROVED", null, 20L, 2L, "Kho 1",
                BigDecimal.valueOf(200), "user", Instant.now(), List.of()
        );

        handler.onOrderCreated(event, "msg-456");

        verify(incomingStockService, never()).processPurchaseOrderCreated(any(), any());
    }
}

