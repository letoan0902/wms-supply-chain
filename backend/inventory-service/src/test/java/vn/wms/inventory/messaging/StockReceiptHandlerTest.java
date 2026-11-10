package vn.wms.inventory.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.inventory.service.StockReceiptService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockReceiptHandlerTest {

    @Mock
    private StockReceiptService stockReceiptService;

    @InjectMocks
    private StockReceiptHandler handler;

    @Test
    @DisplayName("Chuyển tiếp sự kiện order.received sang StockReceiptService")
    void onOrderReceived_Success() {
        ReceivedItemLine line = new ReceivedItemLine(
                100L, "SKU-001", "Sản phẩm A", BigDecimal.TEN, BigDecimal.ZERO,
                "cái", 1L, "BIN-01", "LOT-001", LocalDate.now(), LocalDate.now().plusMonths(6)
        );
        OrderReceivedEvent event = new OrderReceivedEvent(
                1L, "GRN-20261108-0001", 10L, "PO-20261108-0001", 2L, 5L,
                "staff", LocalDate.now(), Instant.now(), List.of(line)
        );

        handler.onOrderReceived(event, "msg-001");

        verify(stockReceiptService).processOrderReceived(eq(event), eq("msg-001"));
    }

    @Test
    @DisplayName("Bỏ qua sự kiện trùng lặp (Idempotency) khi cùng messageId gửi lại")
    void onOrderReceived_DuplicateMessageIgnored() {
        ReceivedItemLine line = new ReceivedItemLine(
                100L, "SKU-001", "Sản phẩm A", BigDecimal.TEN, BigDecimal.ZERO,
                "cái", 1L, "BIN-01", "LOT-001", LocalDate.now(), LocalDate.now().plusMonths(6)
        );
        OrderReceivedEvent event = new OrderReceivedEvent(
                1L, "GRN-20261108-0001", 10L, "PO-20261108-0001", 2L, 5L,
                "staff", LocalDate.now(), Instant.now(), List.of(line)
        );

        handler.onOrderReceived(event, "msg-dup-001");
        handler.onOrderReceived(event, "msg-dup-001");

        verify(stockReceiptService, times(1)).processOrderReceived(any(), any());
    }

    @Test
    @DisplayName("Khi StockReceiptService gặp lỗi thì bỏ đánh dấu đã xử lý để RabbitMQ có thể retry")
    void onOrderReceived_ErrorAllowsRetry() {
        ReceivedItemLine line = new ReceivedItemLine(
                100L, "SKU-001", "Sản phẩm A", BigDecimal.TEN, BigDecimal.ZERO,
                "cái", 1L, "BIN-01", "LOT-001", LocalDate.now(), LocalDate.now().plusMonths(6)
        );
        OrderReceivedEvent event = new OrderReceivedEvent(
                1L, "GRN-20261108-0001", 10L, "PO-20261108-0001", 2L, 5L,
                "staff", LocalDate.now(), Instant.now(), List.of(line)
        );

        doThrow(new RuntimeException("Database error")).when(stockReceiptService)
                .processOrderReceived(any(), eq("msg-retry-001"));

        assertThatThrownBy(() -> handler.onOrderReceived(event, "msg-retry-001"))
                .isInstanceOf(RuntimeException.class);

        // Lần gọi thứ 2 với cùng messageId vẫn được chuyển cho service vì lần 1 bị lỗi
        assertThatThrownBy(() -> handler.onOrderReceived(event, "msg-retry-001"))
                .isInstanceOf(RuntimeException.class);

        verify(stockReceiptService, times(2)).processOrderReceived(any(), eq("msg-retry-001"));
    }

    @Test
    @DisplayName("Bỏ qua sự kiện không hợp lệ hoặc thiếu warehouseId")
    void onOrderReceived_InvalidEventIgnored() {
        handler.onOrderReceived(null, "msg-invalid-1");

        OrderReceivedEvent eventWithoutWarehouse = new OrderReceivedEvent(
                1L, "GRN-001", 10L, "PO-001", null, 5L,
                "staff", LocalDate.now(), Instant.now(), List.of()
        );
        handler.onOrderReceived(eventWithoutWarehouse, "msg-invalid-2");

        verify(stockReceiptService, never()).processOrderReceived(any(), any());
    }
}
