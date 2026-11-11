package vn.wms.inventory.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.service.StockDeductionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler lắng nghe sự kiện đơn hàng xuất kho hoàn tất ({@code order.dispatched}) và tự động
 * thực hiện trừ tồn kho OnHand với Khóa bi quan, giải phóng Reserved (WMS-12.2).
 */
@Component
public class DispatchStockHandler implements OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchStockHandler.class);

    private final StockDeductionService stockDeductionService;
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    public DispatchStockHandler(StockDeductionService stockDeductionService) {
        this.stockDeductionService = stockDeductionService;
    }

    @Override
    public void onOrderDispatched(OrderDispatchedEvent event, String messageId) {
        if (event == null || event.warehouseId() == null) {
            log.warn("Bỏ qua sự kiện order.dispatched vì dữ liệu không hợp lệ hoặc thiếu warehouseId");
            return;
        }

        // Chống xử lý trùng lặp message (Idempotency)
        if (messageId != null && !messageId.isBlank()) {
            if (!processedMessageIds.add(messageId)) {
                log.info("Bỏ qua sự kiện order.dispatched do messageId '{}' đã được xử lý", messageId);
                return;
            }
        }

        log.info("Nhận sự kiện 'order.dispatched' cho đơn {} (ID: {}). Tiến hành trừ tồn kho với Khóa bi quan...",
                event.orderNumber(), event.orderId());

        if (event.items() == null || event.items().isEmpty()) {
            log.warn("Đơn xuất {} không có dòng mặt hàng nào để trừ tồn kho", event.orderNumber());
            return;
        }

        List<DeductStockItemRequest> deductItems = new ArrayList<>();
        for (OrderLine line : event.items()) {
            if (line.productId() != null && line.quantity() != null) {
                deductItems.add(new DeductStockItemRequest(
                        line.productId(),
                        line.skuCode(),
                        line.quantity(),
                        null, // Vị trí tự động allocate theo tồn có sẵn
                        null
                ));
            }
        }

        if (deductItems.isEmpty()) {
            log.warn("Không có mặt hàng hợp lệ trong đơn xuất {}", event.orderNumber());
            return;
        }

        DeductStockRequest request = new DeductStockRequest(
                event.orderId(),
                event.orderNumber(),
                event.warehouseId(),
                "SALES_DISPATCH",
                "Tự động trừ tồn kho khi nhận sự kiện order.dispatched từ order-service",
                deductItems
        );

        try {
            // Tập messageId trong bộ nhớ chỉ chặn trùng khi service còn chạy; nhật ký stock_dispatches trong DB
            // chặn cả trường hợp giao lại sau khi restart hoặc phát lại từ dead-letter (WMS-12.1).
            if (stockDeductionService.deductStockForDispatchEvent(request, event.dispatchedBy(), messageId) == null) {
                return;
            }
            log.info("Đã hoàn tất trừ tồn kho với Khóa bi quan cho đơn xuất {}", event.orderNumber());
        } catch (Exception ex) {
            log.error("Lỗi khi trừ tồn kho cho đơn xuất {}: {}", event.orderNumber(), ex.getMessage(), ex);
            // Chưa trừ được thì bỏ đánh dấu đã xử lý, nếu không lần thử lại sẽ bị coi là trùng và bị bỏ qua
            if (messageId != null && !messageId.isBlank()) {
                processedMessageIds.remove(messageId);
            }
            // Ném lỗi để RabbitMQ retry theo cấu hình retry / dead-letter
            throw ex;
        }
    }
}
