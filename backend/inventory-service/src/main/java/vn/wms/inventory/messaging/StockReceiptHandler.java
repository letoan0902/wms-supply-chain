package vn.wms.inventory.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.inventory.service.StockReceiptService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler lắng nghe sự kiện hàng đã nhập kho thành công ({@code order.received}) từ order-service (WMS-10.3).
 * Tự động tăng OnHand Stock, giảm Incoming Stock, lưu vị trí ô/kệ và quản lý Lô (FR11, FR12, FR13).
 */
@Component
public class StockReceiptHandler implements OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(StockReceiptHandler.class);

    private final StockReceiptService stockReceiptService;
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    public StockReceiptHandler(StockReceiptService stockReceiptService) {
        this.stockReceiptService = stockReceiptService;
    }

    @Override
    public void onOrderReceived(OrderReceivedEvent event, String messageId) {
        if (event == null || event.warehouseId() == null) {
            log.warn("Bỏ qua sự kiện order.received vì dữ liệu không hợp lệ hoặc thiếu warehouseId");
            return;
        }

        // Chống xử lý trùng lặp message (Idempotency)
        if (messageId != null && !messageId.isBlank()) {
            if (!processedMessageIds.add(messageId)) {
                log.info("Bỏ qua sự kiện order.received do messageId '{}' đã được xử lý", messageId);
                return;
            }
        }

        log.info("StockReceiptHandler: Nhận sự kiện 'order.received' cho phiếu nhập {} (PO: {}). Tiến hành tăng OnHand & giảm Incoming stock...",
                event.grnNumber(), event.poNumber());

        try {
            stockReceiptService.processOrderReceived(event, messageId);
            log.info("Đã hoàn tất xử lý tăng OnHand và giảm Incoming cho phiếu nhập {}", event.grnNumber());
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý nhập kho cho phiếu nhập {}: {}", event.grnNumber(), ex.getMessage(), ex);
            // Chưa xử lý thành công thì bỏ đánh dấu đã xử lý để RabbitMQ thử lại
            if (messageId != null && !messageId.isBlank()) {
                processedMessageIds.remove(messageId);
            }
            throw ex;
        }
    }
}
