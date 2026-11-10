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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.wms.common.exception.ApiException;
import vn.wms.common.persistence.AuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Hóa đơn mua hàng (Purchase Bill) liên kết GRN và theo dõi công nợ NCC (FR11, FR13 - WMS-10.3).
 */
@Entity
@Table(name = "purchase_bills", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_bills_grn", columnNames = {"grn_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class PurchaseBill extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã hóa đơn duy nhất: PB-YYYYMMDD-XXXX. */
    @Column(name = "bill_number", nullable = false, unique = true, length = 30)
    private String billNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grn_id", nullable = false)
    private GoodsReceiptNote goodsReceiptNote;

    @Column(name = "grn_number", nullable = false, length = 30)
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

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Tổng giá trị hóa đơn (VNĐ). */
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Số tiền đã thanh toán (VNĐ). */
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Số tiền còn phải trả (VNĐ) = totalAmount - paidAmount. */
    @Column(name = "remaining_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PurchaseBillStatus status = PurchaseBillStatus.UNPAID;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "purchaseBill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseBillItem> items = new ArrayList<>();

    public void addItem(PurchaseBillItem item) {
        items.add(item);
        item.setPurchaseBill(this);
    }

    public void recordPayment(BigDecimal paymentAmount) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Số tiền thanh toán phải lớn hơn 0");
        }
        if (paymentAmount.compareTo(this.remainingAmount) > 0) {
            throw ApiException.badRequest(String.format(
                    "Số tiền thanh toán (%s VNĐ) vượt quá số tiền còn nợ của hóa đơn (%s VNĐ)",
                    paymentAmount, this.remainingAmount));
        }

        this.paidAmount = this.paidAmount.add(paymentAmount);
        this.remainingAmount = this.totalAmount.subtract(this.paidAmount);

        if (this.remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
            this.status = PurchaseBillStatus.PAID;
        } else {
            this.status = PurchaseBillStatus.PARTIALLY_PAID;
        }
    }
}
