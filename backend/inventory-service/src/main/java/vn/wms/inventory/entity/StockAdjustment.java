package vn.wms.inventory.entity;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu điều chỉnh tồn kho (Adjustment In/Out) sinh từ chênh lệch của một đợt kiểm kê (WMS-14.2, FR20).
 * Mỗi đợt có tối đa một phiếu (ràng buộc duy nhất theo doanh nghiệp và đợt).
 */
@Entity
@Table(name = "stock_adjustments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_adjustments_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_stock_adjustments_company_session", columnNames = {"company_id", "session_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class StockAdjustment extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StocktakeSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockAdjustmentStatus status = StockAdjustmentStatus.PENDING_APPROVAL;

    @Column(name = "total_increase_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal totalIncreaseQuantity = BigDecimal.ZERO;

    @Column(name = "total_decrease_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal totalDecreaseQuantity = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @Column(name = "requested_by", length = 50)
    private String requestedBy;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by", length = 50)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StockAdjustmentLine> lines = new ArrayList<>();

    public void addLine(StockAdjustmentLine line) {
        lines.add(line);
        line.setAdjustment(this);
    }
}
