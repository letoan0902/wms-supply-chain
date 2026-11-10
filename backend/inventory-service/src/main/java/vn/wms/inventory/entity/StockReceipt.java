package vn.wms.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

/**
 * Nhật ký phiếu nhập kho (GRN) đã được cộng tồn từ sự kiện {@code order.received} (WMS-10.3).
 * Ràng buộc duy nhất (company_id, grn_id) bảo đảm mỗi GRN chỉ cộng tồn một lần dù sự kiện bị giao lại
 * hoặc service khởi động lại (thay cho tập messageId trong bộ nhớ vốn mất khi restart).
 */
@Entity
@Table(name = "stock_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_receipts_company_grn", columnNames = {"company_id", "grn_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class StockReceipt extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grn_id", nullable = false)
    private Long grnId;

    @Column(name = "grn_number", length = 30)
    private String grnNumber;

    @Column(name = "po_id")
    private Long poId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "message_id", length = 100)
    private String messageId;
}
