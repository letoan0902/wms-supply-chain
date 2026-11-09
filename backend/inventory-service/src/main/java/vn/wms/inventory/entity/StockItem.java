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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Tồn kho của một sản phẩm tại một vị trí (theo lô nếu có). */
@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
public class StockItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id sản phẩm bên product-service. Chỉ là tham chiếu logic, không có khóa ngoại vì khác database. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_location_id", nullable = false)
    private BinLocation binLocation;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    /** Ngày sản xuất (WMS-10.2 - FR12). */
    @Column(name = "mfg_date")
    private LocalDate mfgDate;

    /** Hạn sử dụng (WMS-10.2 - FR12). */
    @Column(name = "exp_date")
    private LocalDate expDate;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Số lượng đã giữ chỗ cho đơn bán / điều chuyển, chưa xuất kho. */
    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    /** Khóa lô (WMS-10.2 - FR12): Lô hết hạn hoặc bị phong tỏa không được phép xuất kho. */
    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    /** Lý do khóa lô (hết hạn, phế phẩm, cách ly kiểm định...). */
    @Column(name = "locked_reason")
    private String lockedReason;

    public BigDecimal getAvailableQuantity() {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal res = reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
        return qty.subtract(res).max(BigDecimal.ZERO);
    }

    public boolean isExpired(LocalDate today) {
        LocalDate ref = today != null ? today : LocalDate.now();
        return expDate != null && !expDate.isAfter(ref);
    }

    public Long getDaysUntilExpiry(LocalDate today) {
        if (expDate == null) {
            return null;
        }
        LocalDate ref = today != null ? today : LocalDate.now();
        return ChronoUnit.DAYS.between(ref, expDate);
    }
}
