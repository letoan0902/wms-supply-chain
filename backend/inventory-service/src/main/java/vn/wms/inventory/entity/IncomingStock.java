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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.persistence.AuditableEntity;

import java.math.BigDecimal;

/**
 * Quản lý chỉ tiêu hàng đang trên đường về kho (Incoming Stock - WMS-09.2).
 * Được tự động tạo khi Đơn mua hàng (PO) được phê duyệt (APPROVED) và sẽ được trừ dần khi thực hiện nhập kho (GRN - WMS-10.3).
 */
@Entity
@Table(name = "incoming_stocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomingStock extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** ID sản phẩm bên product-service (tham chiếu logic). */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(name = "product_name")
    private String productName;

    /** ID đơn mua hàng bên order-service. */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Mã đơn mua hàng (ví dụ PO-20261104-0001). */
    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;

    /** Số lượng hàng dự kiến về kho theo đơn PO. */
    @Column(nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(length = 20)
    private String uom;

    /** Trạng thái hàng chờ: INCOMING (đang về), RECEIVED (đã nhập xong), CANCELLED (hủy). */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "INCOMING";

    /** ID thông điệp RabbitMQ để đảm bảo tính Idempotency (chống trùng lặp). */
    @Column(name = "message_id", length = 100)
    private String messageId;
}

