package vn.wms.auth.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.persistence.AuditableEntity;

/**
 * Thực thể Doanh nghiệp (Company / Tenant) sử dụng nền tảng SaaS.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "tax_code", unique = true, length = 30)
    private String taxCode;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
