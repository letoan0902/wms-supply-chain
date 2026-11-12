package vn.wms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

/**
 * Nhật ký sự kiện {@code order.dispatched} đã được trừ tồn (WMS-12.1).
 * Ràng buộc duy nhất (company_id, message_id) bảo đảm mỗi phiếu xuất kho chỉ trừ tồn một lần dù sự kiện bị giao lại
 * hoặc service khởi động lại (tập messageId trong bộ nhớ của DispatchStockHandler mất khi restart).
 */
@Entity
@Table(name = "stock_dispatches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_dispatches_company_message", columnNames = {"company_id", "message_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class StockDispatch extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", length = 30)
    private String orderNumber;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;
}
