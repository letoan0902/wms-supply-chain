package vn.wms.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.common.tenant.TenantContext;

import java.util.List;

/**
 * Listener nhận sự kiện Spring sau khi transaction commit thành công (@TransactionalEventListener AFTER_COMMIT),
 * đảm bảo chỉ phát tán sự kiện sang RabbitMQ khi dữ liệu đơn mua hàng đã được lưu chắc chắn vào CSDL.
 *
 * <p>Phát {@link OrderCreatedEvent} qua {@link WmsEventPublisher} để message có messageId, timestamp và header
 * nguồn như mọi sự kiện khác (docs/MESSAGING.md mục 7); consumer dùng messageId để bỏ qua message giao lại.
 */
@Component
public class PurchaseOrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderEventListener.class);

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseOrderApproved(PurchaseOrderApprovedEvent event) {
        if (wmsEventPublisher == null) {
            log.info("WmsEventPublisher không khả dụng, bỏ qua gửi sự kiện '{}'", WmsEvents.RoutingKeys.ORDER_CREATED);
            return;
        }

        try {
            List<OrderLine> lines = event.items() == null ? List.of() :
                    event.items().stream()
                            .map(it -> new OrderLine(
                                    it.productId(), it.skuCode(), it.productName(), it.quantity(), it.uom(), it.unitPrice()))
                            .toList();

            OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent(
                    event.orderId(),
                    event.poNumber(),
                    OrderType.PURCHASE,
                    event.status(),
                    event.supplierId(),
                    null,
                    event.warehouseId(),
                    event.warehouseName(),
                    event.totalAmount(),
                    "SYSTEM",
                    event.timestamp(),
                    lines
            );
            // Listener đã chạy sau commit nên gọi publish trực tiếp, không cần publishAfterCommit. Phát dưới danh nghĩa
            // doanh nghiệp của đơn để header x-wms-company-id luôn đúng, không phụ thuộc ngữ cảnh của luồng lúc này.
            String messageId = TenantContext.callAs(event.companyId(),
                    () -> wmsEventPublisher.publish(WmsEvents.RoutingKeys.ORDER_CREATED, orderCreatedEvent));
            log.info("Đã gửi sự kiện '{}' sau commit cho đơn mua hàng: {} (messageId={})",
                    WmsEvents.RoutingKeys.ORDER_CREATED, event.poNumber(), messageId);
        } catch (Exception e) {
            log.warn("Không thể gửi sự kiện '{}' sang RabbitMQ cho đơn mua hàng {}: {}",
                    WmsEvents.RoutingKeys.ORDER_CREATED, event.poNumber(), e.getMessage());
        }
    }
}
