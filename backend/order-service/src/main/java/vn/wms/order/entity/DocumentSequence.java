package vn.wms.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.wms.common.tenant.TenantEntity;

/**
 * Bộ đếm số chứng từ của một doanh nghiệp theo tiền tố, ví dụ {@code PO-20261110-} (WMS-22.4b).
 * Mỗi doanh nghiệp có bộ đếm riêng nên hai doanh nghiệp cùng có thể có {@code PO-20261110-0001}.
 */
@Entity
@Table(name = "document_sequences", uniqueConstraints = @UniqueConstraint(
        name = "uk_document_sequences_company_prefix", columnNames = {"company_id", "seq_prefix"}))
@Getter
@NoArgsConstructor
public class DocumentSequence extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seq_prefix", nullable = false, length = 30)
    private String prefix;

    /** Số thứ tự đã cấp gần nhất. */
    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    public DocumentSequence(String prefix, long lastNumber) {
        this.prefix = prefix;
        this.lastNumber = lastNumber;
    }

    /** Cấp số tiếp theo và trả về số vừa cấp. */
    public long increment() {
        lastNumber++;
        return lastNumber;
    }
}
