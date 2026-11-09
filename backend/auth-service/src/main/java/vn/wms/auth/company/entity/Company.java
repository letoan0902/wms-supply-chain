package vn.wms.auth.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.wms.common.persistence.AuditableEntity;

import java.time.Instant;

/**
 * Doanh nghiệp (khách hàng của nền tảng). Mỗi tài khoản thuộc đúng một doanh nghiệp.
 * Bảng này không dùng TenantEntity: auth-service lọc tường minh theo companyId của người đang đăng nhập.
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

    /** Mã doanh nghiệp dạng CTY cộng 6 chữ số theo id, ví dụ CTY000001. */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CompanyStatus status = CompanyStatus.ACTIVE;

    @Column(name = "activated_at")
    private Instant activatedAt;

    /** Doanh nghiệp đang hoạt động (đăng nhập và làm mới token được). */
    public boolean isActive() {
        return status == CompanyStatus.ACTIVE;
    }
}
