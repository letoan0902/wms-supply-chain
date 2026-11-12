package vn.wms.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.tenant.TenantEntity;

import java.math.BigDecimal;

/**
 * Khách hàng / Đại lý (Customers - FR14, WMS-08.2).
 */
@Entity
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(
        name = "uk_customers_company_code", columnNames = {"company_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Customer extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType = CustomerType.RETAIL;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "payment_terms", length = 50)
    private String paymentTerms;

    @Column(name = "credit_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "current_debt", nullable = false, precision = 18, scale = 2)
    private BigDecimal currentDebt = BigDecimal.ZERO;

    @Column(name = "debt_locked", nullable = false)
    private boolean debtLocked = false;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
