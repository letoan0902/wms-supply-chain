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
import java.time.Instant;

/**
 * Dòng kiểm kê theo (vị trí, sản phẩm, số lô): số tồn hệ thống chụp lúc bắt đầu, số đếm thực tế và chênh lệch.
 * Dòng {@code unexpected = true} là hàng tìm thấy khi đếm nhưng không có trong ảnh chụp (tồn hệ thống bằng 0).
 * Số tồn hệ thống không bao giờ được trả về qua API đếm (BR-12).
 */
@Entity
@Table(name = "stocktake_lines")
@Getter
@Setter
@NoArgsConstructor
public class StocktakeLine extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StocktakeSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_location_id", nullable = false)
    private BinLocation binLocation;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    /** Tồn thực tế (OnHand) trên hệ thống lúc chụp. */
    @Column(name = "system_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal systemQuantity = BigDecimal.ZERO;

    /** Số lượng đang giữ chỗ cho đơn lúc chụp. */
    @Column(name = "system_reserved_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal systemReservedQuantity = BigDecimal.ZERO;

    /** Số đếm thực tế; null nghĩa là chưa đếm. */
    @Column(name = "counted_quantity", precision = 18, scale = 3)
    private BigDecimal countedQuantity;

    /** Chênh lệch = số đếm - tồn hệ thống, tính khi kết thúc đếm; null khi chưa tính. */
    @Column(name = "variance_quantity", precision = 18, scale = 3)
    private BigDecimal varianceQuantity;

    @Column(name = "count_times", nullable = false)
    private int countTimes = 0;

    @Column(name = "last_counted_by", length = 50)
    private String lastCountedBy;

    @Column(name = "last_counted_at")
    private Instant lastCountedAt;

    @Column(nullable = false)
    private boolean unexpected = false;

    @Column(name = "recount_requested", nullable = false)
    private boolean recountRequested = false;

    /** Lý do chênh lệch do Quản lý kho ghi. */
    @Column(length = 255)
    private String reason;

    public boolean isCounted() {
        return countedQuantity != null;
    }
}
