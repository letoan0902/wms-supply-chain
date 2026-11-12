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

import java.time.Instant;

/**
 * Thời điểm bắt đầu ghi sổ kho của một doanh nghiệp đã có dữ liệu kho trước khi có sổ kho (WMS-17.1).
 *
 * <p>Migration V13 tạo một dòng cho mỗi doanh nghiệp đang có kho, cùng thời điểm với các dòng số dư mở sổ.
 * Biến động trước thời điểm này không được ghi lại nên báo cáo trả {@code null} cho tồn đầu kỳ, nhập, xuất của
 * kỳ bắt đầu từ trước thời điểm này. Doanh nghiệp tạo sau migration không có dòng nào: mọi biến động của họ
 * đều nằm trong sổ kho ngay từ đầu.
 */
@Entity
@Table(name = "stock_ledger_starts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_ledger_starts_company", columnNames = {"company_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class StockLedgerStart extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Thời điểm bắt đầu ghi sổ kho (UTC), trùng thời điểm của các dòng số dư mở sổ. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
}
