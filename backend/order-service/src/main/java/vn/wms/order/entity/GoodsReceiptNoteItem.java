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
import vn.wms.common.persistence.AuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Chi tiết dòng hàng nhập kho trong GRN (FR11, FR12 - WMS-10.1, WMS-10.2).
 */
@Entity
@Table(name = "goods_receipt_note_items")
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceiptNoteItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grn_id", nullable = false)
    private GoodsReceiptNote goodsReceiptNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(length = 30)
    private String uom;

    /** Số lượng đặt theo PO ban đầu. */
    @Column(name = "ordered_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal orderedQuantity = BigDecimal.ZERO;

    /** Số lượng đạt chuẩn cho phép nhập kho (Accepted). */
    @Column(name = "accepted_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal acceptedQuantity = BigDecimal.ZERO;

    /** Số lượng bị từ chối / phế phẩm / lỗi hỏng (Rejected). */
    @Column(name = "rejected_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Thành tiền dòng hàng đạt chuẩn = accepted_quantity * unit_price. */
    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    /** Lý do từ chối nếu có sản phẩm hỏng/lỗi. */
    @Column(name = "rejected_reason", length = 255)
    private String rejectedReason;

    /** Số lô hàng (Batch/Lot number - FR12). */
    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    /** Ngày sản xuất (Mfg date - FR12). */
    @Column(name = "mfg_date")
    private LocalDate mfgDate;

    /** Hạn sử dụng (Exp date - FR12). */
    @Column(name = "exp_date")
    private LocalDate expDate;

    /** ID vị trí ô/kệ cất hàng trong kho. */
    @Column(name = "location_id")
    private Long locationId;

    /** Mã vị trí ô/kệ (ví dụ: A-01-02). */
    @Column(name = "location_code", length = 50)
    private String locationCode;

    @Column(length = 255)
    private String note;

    /**
     * Tổng số lượng thực tế nhận được (Accepted + Rejected).
     */
    public BigDecimal getTotalReceivedQuantity() {
        BigDecimal acc = acceptedQuantity != null ? acceptedQuantity : BigDecimal.ZERO;
        BigDecimal rej = rejectedQuantity != null ? rejectedQuantity : BigDecimal.ZERO;
        return acc.add(rej);
    }
}
