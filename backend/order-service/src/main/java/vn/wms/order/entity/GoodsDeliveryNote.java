package vn.wms.order.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.persistence.AuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu xuất kho (Goods Delivery Note - GDN) (WMS-12.1 - FR17).
 */
@Entity
@Table(name = "goods_delivery_notes")
@Getter
@Setter
@NoArgsConstructor
public class GoodsDeliveryNote extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gdn_number", nullable = false, unique = true, length = 30)
    private String gdnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "so_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(name = "so_number", nullable = false, length = 30)
    private String soNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_name", length = 100)
    private String warehouseName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsDeliveryStatus status = GoodsDeliveryStatus.COMPLETED;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate = LocalDate.now();

    @Column(name = "total_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Column(length = 500)
    private String note;

    @Column(name = "dispatched_by", length = 50)
    private String dispatchedBy;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @OneToMany(mappedBy = "goodsDeliveryNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsDeliveryNoteItem> items = new ArrayList<>();

    public void addItem(GoodsDeliveryNoteItem item) {
        items.add(item);
        item.setGoodsDeliveryNote(this);
    }
}
