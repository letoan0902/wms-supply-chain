package vn.wms.product.entity;

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

/**
 * Quy đổi đơn vị tính (Unit Conversion) cho từng sản phẩm (SKU).
 * <p>
 * Ví dụ: Với sản phẩm Bia Tiger:
 * 1 Thùng (fromUom) = 4 Lốc (toUom) với conversionFactor = 4.0
 * 1 Lốc (fromUom) = 6 Lon (toUom) với conversionFactor = 6.0
 */
@Entity
@Table(name = "unit_conversions")
@Getter
@Setter
@NoArgsConstructor
public class UnitConversion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Đơn vị nguồn / đóng gói lớn hơn (ví dụ: Thùng, Lốc). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_uom_id", nullable = false)
    private Uom fromUom;

    /** Đơn vị đích / đóng gói nhỏ hơn (ví dụ: Lốc, Chai/Lon). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_uom_id", nullable = false)
    private Uom toUom;

    /** Hệ số quy đổi (1 fromUom = conversionFactor * toUom). */
    @Column(name = "conversion_factor", precision = 18, scale = 6, nullable = false)
    private BigDecimal conversionFactor;

    /** Mã vạch riêng biệt cho quy cách đóng gói này (ví dụ mã vạch trên thùng carton). */
    @Column(length = 64)
    private String barcode;

    /** Ghi chú / mô tả quy cách (ví dụ "Thùng 24 lon 330ml"). */
    @Column(length = 255)
    private String description;
}
