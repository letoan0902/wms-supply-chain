package vn.wms.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.wms.common.messaging.WmsEvents;

/**
 * Listener nhận sự kiện Spring sau khi transaction commit thành công (@TransactionalEventListener AFTER_COMMIT),
 * đảm bảo chỉ phát tán sự kiện sang RabbitMQ khi dữ liệu đơn mua hàng đã được lưu chắc chắn vào CSDL.
 */
@Component
public class PurchaseOrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventListener.class);

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderApproved(PurchaseOrderApprovedEvent event) {
        if (rabbitTemplate == null) {
            log.info("RabbitTemplate không khả dụng, bỏ qua gửi sự kiện '{}'", WmsEvents.RoutingKeys.ORDER_CREATED);
            return;
        }

        try {
            rabbitTemplate.convertAndSend(WmsEvents.EXCHANGE, WmsEvents.RoutingKeys.ORDER_CREATED, event);
            log.info("Đã gửi sự kiện '{}' sau commit cho đơn mua hàng: {}",
                    WmsEvents.RoutingKeys.ORDER_CREATED, event.poNumber());
        } catch (Exception e) {
            log.warn("Không thể gửi sự kiện '{}' sang RabbitMQ cho đơn mua hàng {}: {}",
                    WmsEvents.RoutingKeys.ORDER_CREATED, event.poNumber(), e.getMessage());
        }
    }
}
