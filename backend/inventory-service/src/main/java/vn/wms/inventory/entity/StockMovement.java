package vn.wms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng sổ kho (WMS-17.1): mỗi lần số lượng tồn {@code stock_items.quantity} thay đổi thì có đúng một dòng
 * ghi cùng transaction, nên tồn đầu kỳ, nhập, xuất, tồn cuối kỳ của mọi kỳ đều tính lại được từ bảng này.
 *
 * <p>Sổ kho là lịch sử bất biến: không có khóa ngoại tới kho, vị trí hay dòng tồn để dòng sổ vẫn còn khi các
 * bản ghi đó bị xóa; mã vị trí, mã SKU, tên sản phẩm được chụp lại tại thời điểm phát sinh.
 */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_company_wh_product_time",
                columnList = "company_id, warehouse_id, product_id, occurred_at"),
        @Index(name = "idx_stock_movements_company_time", columnList = "company_id, occurred_at"),
        @Index(name = "idx_stock_movements_company_lot", columnList = "company_id, lot_no, product_id"),
        @Index(name = "idx_stock_movements_company_document",
                columnList = "company_id, document_type, document_number")
})
@Getter
@Setter
@NoArgsConstructor
public class StockMovement extends TenantEntity {

    /** Chứng từ là phiếu nhập kho GRN của order-service. */
    public static final String DOC_GOODS_RECEIPT = "GOODS_RECEIPT";
    /** Chứng từ là đơn bán SO (sự kiện xuất kho chỉ mang mã đơn bán, chưa mang mã phiếu xuất GDN). */
    public static final String DOC_SALES_ORDER = "SALES_ORDER";
    /** Chứng từ là phiếu điều chuyển nội bộ. */
    public static final String DOC_STOCK_TRANSFER = "STOCK_TRANSFER";
    /** Số dư mở sổ do migration V13 tạo, không có chứng từ gốc. */
    public static final String DOC_OPENING_BALANCE = "OPENING_BALANCE";
    /** Chứng từ khác (xuất kho qua API trừ tồn với lý do tự do). */
    public static final String DOC_OTHER = "OTHER";
    /** Tham chiếu tới đơn mua PO (dùng cho dòng nhập kho). */
    public static final String REF_PURCHASE_ORDER = "PURCHASE_ORDER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Thời điểm tồn kho thay đổi (UTC). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private StockMovementType movementType;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "bin_location_id")
    private Long binLocationId;

    /** Mã vị trí tại thời điểm phát sinh. */
    @Column(name = "bin_location_code", length = 80)
    private String binLocationCode;

    @Column(name = "stock_item_id")
    private Long stockItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_code", length = 64)
    private String skuCode;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(length = 20)
    private String uom;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "mfg_date")
    private LocalDate mfgDate;

    @Column(name = "exp_date")
    private LocalDate expDate;

    @Column(name = "quantity_in", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityIn = BigDecimal.ZERO;

    @Column(name = "quantity_out", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityOut = BigDecimal.ZERO;

    /** Loại chứng từ gốc, xem các hằng {@code DOC_*}. */
    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "document_number", length = 64)
    private String documentNumber;

    /** Loại chứng từ tham chiếu (ví dụ đơn mua PO của phiếu nhập). */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    /** ID đối tác bên order-service (nhà cung cấp của phiếu nhập), chỉ là tham chiếu logic. */
    @Column(name = "partner_id")
    private Long partnerId;

    /** Kho phía bên kia của phiếu điều chuyển (kho đích với dòng xuất, kho nguồn với dòng nhận). */
    @Column(name = "counterpart_warehouse_id")
    private Long counterpartWarehouseId;

    @Column(length = 255)
    private String reason;

    /** Người thực hiện nghiệp vụ (người xác nhận phiếu nhập, người duyệt xuất, người nhận hàng). */
    @Column(name = "performed_by", length = 50)
    private String performedBy;
}
