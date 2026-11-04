package vn.wms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.persistence.AuditableEntity;

import java.math.BigDecimal;

/**
 * Bản ghi giữ hàng ảo (Soft Reservation) cho đơn bán hàng (FR16 - WMS-11.2).
 */
@Entity
@Table(name = "stock_reservations")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    /** Trạng thái giữ hàng: ACTIVE (đang giữ), RELEASED (đã giải phóng), FULFILLED (đã xuất kho GDN). */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "message_id", length = 100)
    private String messageId;
}
