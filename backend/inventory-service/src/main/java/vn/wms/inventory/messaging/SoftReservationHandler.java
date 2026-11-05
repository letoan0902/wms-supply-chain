package vn.wms.inventory.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.inventory.service.StockReservationService;

/**
 * Handler nhận sự kiện đơn hàng được tạo (order.created) và thực hiện Giữ hàng ảo (Soft Reservation)
 * cho Đơn bán hàng SO (OrderType.SALES) (FR16 - WMS-11.2).
 */
@Component
public class SoftReservationHandler implements OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SoftReservationHandler.class);

    private final StockReservationService stockReservationService;

    public SoftReservationHandler(StockReservationService stockReservationService) {
        this.stockReservationService = stockReservationService;
    }

    @Override
    public void onOrderCreated(OrderCreatedEvent event, String messageId) {
        if (event == null) {
            return;
        }

        // Chỉ thực hiện giữ hàng ảo đối với Đơn bán hàng (SALES)
        if (event.orderType() == OrderType.SALES) {
            log.info("Nhận sự kiện 'order.created' loại SALES cho đơn {}. Bắt đầu xử lý giữ hàng ảo...",
                    event.orderNumber());
            stockReservationService.reserveStock(event, messageId);
        } else {
            log.debug("Bỏ qua giữ hàng ảo cho đơn {} vì không phải đơn bán hàng (loại: {})",
                    event.orderNumber(), event.orderType());
        }
    }
}
