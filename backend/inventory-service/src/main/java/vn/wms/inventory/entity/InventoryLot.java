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
import vn.wms.common.tenant.TenantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Bảng inventory_lots: Lưu trữ chi tiết từng Lô hàng (Số lô, Mfg Date, Exp Date)
 * theo Vị trí ô/kệ lưu trữ (Bin ID) để phục vụ xuất FIFO/FEFO và khóa lô hết hạn (WMS-10.2 - FR12).
 * Kế thừa TenantEntity để phân tách theo doanh nghiệp (WMS-22.4a).
 */
@Entity
@Table(name = "inventory_lots")
@Getter
@Setter
@NoArgsConstructor
public class InventoryLot extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Số lô hàng (Batch / Lot Number). */
    @Column(name = "lot_number", length = 50, nullable = false)
    private String lotNumber;

    /** ID sản phẩm bên product-service (tham chiếu logic). */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Vị trí ô/kệ lưu trữ lô hàng này (Bin ID). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_location_id", nullable = false)
    private BinLocation binLocation;

    /** Ngày sản xuất (Mfg Date). */
    @Column(name = "mfg_date")
    private LocalDate mfgDate;

    /** Hạn sử dụng (Exp Date). */
    @Column(name = "exp_date")
    private LocalDate expDate;

    /** Số lượng tồn thực tế của lô tại ô này (OnHand). */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Số lượng đã giữ chỗ cho đơn bán / điều chuyển chưa xuất kho (Reserved). */
    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    /** Trạng thái khóa lô: true nếu hết hạn hoặc bị phong tỏa không cho phép xuất kho. */
    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    /** Lý do khóa lô (Hết hạn sử dụng, kiểm định, hư hỏng...). */
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
