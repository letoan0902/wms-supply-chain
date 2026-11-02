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

/** Sản phẩm / mã hàng (SKU) quản lý trong kho. */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã SKU, duy nhất trong toàn hệ thống. */
    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    /** Mã vạch (EAN/UPC...), có thể chưa có nhưng nếu có phải duy nhất. */
    @Column(unique = true, length = 64)
    private String barcode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uom_id", nullable = false)
    private Uom uom;

    /** Giá nhập dự kiến / tiêu chuẩn (VNĐ). */
    @Column(name = "purchase_price", precision = 18, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    /** Giá bán niêm yết (VNĐ). */
    @Column(name = "selling_price", precision = 18, scale = 2)
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    /** Thuế suất VAT (%), ví dụ: 8.00, 10.00. */
    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    /** URL ảnh sản phẩm. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Cờ quản lý theo Lô sản xuất (Batch / Lot). */
    @Column(name = "manage_by_lot", nullable = false)
    private boolean manageByLot = false;

    /** Cờ quản lý theo Hạn sử dụng (Expiration Date). */
    @Column(name = "manage_by_expiration", nullable = false)
    private boolean manageByExpiration = false;

    /** Mức tồn an toàn tối thiểu (Safety Stock). */
    @Column(name = "safety_stock", precision = 18, scale = 3)
    private BigDecimal safetyStock = BigDecimal.ZERO;

    /** Điểm đặt hàng lại (Reorder Point). */
    @Column(name = "reorder_point", precision = 18, scale = 3)
    private BigDecimal reorderPoint = BigDecimal.ZERO;

    /** Mức tồn kho tối đa (Max Stock). */
    @Column(name = "max_stock", precision = 18, scale = 3)
    private BigDecimal maxStock = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
