package vn.wms.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.wms.common.persistence.AuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu nhập kho (Goods Receipt Note - GRN) theo đơn mua hàng PO (FR11 - WMS-10.1).
 */
@Entity
@Table(name = "goods_receipt_notes")
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceiptNote extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã phiếu nhập duy nhất: GRN-YYYYMMDD-XXXX. */
    @Column(name = "grn_number", nullable = false, unique = true, length = 30)
    private String grnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "po_number", nullable = false, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "supplier_name", length = 150)
    private String supplierName;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_name", length = 100)
    private String warehouseName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GoodsReceiptStatus status = GoodsReceiptStatus.COMPLETED;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    /** Tổng số lượng thực nhận đạt chuẩn chất lượng (Accepted). */
    @Column(name = "total_accepted_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal totalAcceptedQuantity = BigDecimal.ZERO;

    /** Tổng số lượng bị từ chối / phế phẩm / lỗi hỏng (Rejected). */
    @Column(name = "total_rejected_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal totalRejectedQuantity = BigDecimal.ZERO;

    /** Tổng giá trị lô hàng thực tế nhập kho (VNĐ). */
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String note;

    @Column(name = "received_by", length = 50)
    private String receivedBy;

    @OneToMany(mappedBy = "goodsReceiptNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsReceiptNoteItem> items = new ArrayList<>();

    public void addItem(GoodsReceiptNoteItem item) {
        items.add(item);
        item.setGoodsReceiptNote(this);
    }

    public void clearItems() {
        items.clear();
    }
}
