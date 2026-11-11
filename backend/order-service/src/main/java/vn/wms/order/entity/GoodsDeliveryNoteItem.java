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
 * Dòng mặt hàng trong Phiếu xuất kho (GDN Items - WMS-12.1).
 */
@Entity
@Table(name = "goods_delivery_note_items")
@Getter
@Setter
@NoArgsConstructor
public class GoodsDeliveryNoteItem extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gdn_id", nullable = false)
    private GoodsDeliveryNote goodsDeliveryNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "so_item_id", nullable = false)
    private SalesOrderItem salesOrderItem;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(length = 30)
    private String uom;

    @Column(name = "ordered_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal orderedQuantity = BigDecimal.ZERO;

    @Column(name = "delivered_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal deliveredQuantity = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "location_code", length = 50)
    private String locationCode;

    @Column(length = 255)
    private String note;
}
