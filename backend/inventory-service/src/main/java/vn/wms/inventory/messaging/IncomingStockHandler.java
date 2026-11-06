package vn.wms.inventory.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.inventory.service.IncomingStockService;

/**
 * Xử lý sự kiện đơn hàng phục vụ phân hệ Quản lý Kho (WMS-09.2).
 * Tự động đăng ký với {@link OrderEventsListener} để nhận thông điệp khi đơn mua hàng (PO) được phê duyệt.
 */
@Component
public class IncomingStockHandler implements OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IncomingStockHandler.class);

    private final IncomingStockService incomingStockService;

    public IncomingStockHandler(IncomingStockService incomingStockService) {
        this.incomingStockService = incomingStockService;
    }

    /**
     * Nhận sự kiện đơn mua hàng được duyệt: tăng chỉ tiêu hàng đang trên đường về (Incoming Stock) (FR10).
     */
    @Override
    public void onOrderCreated(OrderCreatedEvent event, String messageId) {
        if (event == null || event.orderType() != OrderType.PURCHASE) {
            return;
        }

        log.info("IncomingStockHandler: Bắt đầu xử lý incoming stock cho đơn mua: orderNumber='{}', khoId={}, messageId={}",
                event.orderNumber(), event.warehouseId(), messageId);

        try {
            incomingStockService.processPurchaseOrderCreated(event, messageId);
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý incoming stock cho đơn mua {}: {}", event.orderNumber(), ex.getMessage(), ex);
            throw ex; // Để Spring AMQP retry theo cấu hình
        }
    }
}

