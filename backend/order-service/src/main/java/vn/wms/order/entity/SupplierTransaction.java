package vn.wms.order.entity;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lịch sử giao dịch & công nợ nhà cung cấp (FR09 - WMS-08.1).
 */
@Entity
@Table(name = "supplier_transactions")
@Getter
@Setter
@NoArgsConstructor
public class SupplierTransaction extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private SupplierTransactionType transactionType;

    @Column(name = "reference_code", length = 50)
    private String referenceCode;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 500)
    private String note;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate = Instant.now();
}
