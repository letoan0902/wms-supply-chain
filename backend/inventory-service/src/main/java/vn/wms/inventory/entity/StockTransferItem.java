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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

import java.math.BigDecimal;

/**
 * Chi tiết dòng sản phẩm trong lệnh điều chuyển kho (WMS-13.1).
 * Kế thừa TenantEntity để tự động gắn company_id theo doanh nghiệp.
 */
@Entity
@Table(name = "stock_transfer_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferItem extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    /** ID sản phẩm bên product-service (tham chiếu logic). */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(name = "product_name")
    private String productName;

    @Column(length = 20)
    private String uom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_bin_location_id")
    private BinLocation sourceBinLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_bin_location_id")
    private BinLocation destinationBinLocation;

    /** Số lượng dự kiến điều chuyển. */
    @Column(nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Số lượng thực tế đã xuất kho nguồn (ghi nhận khi duyệt xuất). */
    @Column(name = "transferred_quantity", nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal transferredQuantity = BigDecimal.ZERO;

    /** Số lượng thực nhận tại kho đích (WMS-13.2). */
    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    /** Số lượng hư hỏng hoặc mất mát phát hiện tại kho đích (WMS-13.2). */
    @Column(name = "damaged_quantity", nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal damagedQuantity = BigDecimal.ZERO;

    @Column(length = 255)
    private String notes;
}

