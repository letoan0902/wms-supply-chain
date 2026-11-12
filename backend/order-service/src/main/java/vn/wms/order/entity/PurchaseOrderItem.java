package vn.wms.order.entity;

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
import vn.wms.common.tenant.TenantEntity;

import java.math.BigDecimal;

/**
 * Chi tiết dòng hàng trong đơn mua hàng (PO Items - FR10, WMS-09.1, WMS-10.1).
 */
@Entity
@Table(name = "po_items")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderItem extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(length = 30)
    private String uom;

    /** Số lượng đặt mua theo PO. */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Tổng số lượng thực tế đã nhập kho đạt chuẩn qua các đợt GRN (FR11 - WMS-10.1). */
    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(length = 255)
    private String note;

    /**
     * Tính số lượng còn lại cần nhập kho của dòng PO.
     */
    public BigDecimal getRemainingQuantity() {
        BigDecimal ordered = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal received = receivedQuantity != null ? receivedQuantity : BigDecimal.ZERO;
        return ordered.subtract(received).max(BigDecimal.ZERO);
    }
}
