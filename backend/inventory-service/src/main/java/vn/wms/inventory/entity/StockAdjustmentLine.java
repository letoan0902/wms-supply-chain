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

/**
 * Dòng phiếu điều chỉnh: chênh lệch của một bộ (vị trí, sản phẩm, số lô). {@code quantityBefore} và
 * {@code quantityAfter} ghi tồn tại vị trí ngay trước và sau khi áp dụng (có giá trị khi phiếu đã duyệt).
 */
@Entity
@Table(name = "stock_adjustment_lines")
@Getter
@Setter
@NoArgsConstructor
public class StockAdjustmentLine extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private StockAdjustment adjustment;

    /** Dòng kiểm kê gốc (tham chiếu để truy vết). */
    @Column(name = "stocktake_line_id")
    private Long stocktakeLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_location_id", nullable = false)
    private BinLocation binLocation;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "system_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "counted_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal countedQuantity;

    /** Chênh lệch = số đếm - tồn hệ thống; dương là điều chỉnh tăng, âm là điều chỉnh giảm. */
    @Column(name = "difference_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal differenceQuantity;

    @Column(name = "quantity_before", precision = 18, scale = 3)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", precision = 18, scale = 3)
    private BigDecimal quantityAfter;

    @Column(length = 255)
    private String reason;
}
