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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu điều chuyển hàng hóa nội bộ giữa các kho (Stock Transfer - FR08, WMS-13.1).
 * Kế thừa TenantEntity để tự động phân tách theo doanh nghiệp.
 */
@Entity
@Table(name = "stock_transfers", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_transfers_company_number", columnNames = {"company_id", "transfer_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_number", nullable = false, length = 32)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StockTransferStatus status = StockTransferStatus.DRAFT;

    @Column(name = "transfer_date")
    private Instant transferDate;

    @Column(name = "expected_delivery_date")
    private Instant expectedDeliveryDate;

    @Column(length = 500)
    private String notes;

    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "received_by", length = 50)
    private String receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockTransferItem> items = new ArrayList<>();

    public void addItem(StockTransferItem item) {
        items.add(item);
        item.setTransfer(this);
    }

    public void removeItem(StockTransferItem item) {
        items.remove(item);
        item.setTransfer(null);
    }
}

