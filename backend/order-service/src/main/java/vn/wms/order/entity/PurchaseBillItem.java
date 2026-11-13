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
 * Chi tiết dòng hàng trong Hóa đơn mua hàng (Purchase Bill Item - FR13 - WMS-10.3).
 */
@Entity
@Table(name = "purchase_bill_items")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseBillItem extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private PurchaseBill purchaseBill;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(length = 30)
    private String uom;

    /** Số lượng hàng thực nhận đạt chuẩn tính vào hóa đơn. */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;
}
