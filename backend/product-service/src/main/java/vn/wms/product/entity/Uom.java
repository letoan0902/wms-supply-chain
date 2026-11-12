package vn.wms.product.entity;

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
 * Đơn vị tính (Unit of Measure), ví dụ PCS (cái), BOX (hộp), KG. Mỗi doanh nghiệp có bộ đơn vị tính riêng, mã duy
 * nhất trong doanh nghiệp.
 */
@Entity
@Table(name = "uoms", uniqueConstraints = @UniqueConstraint(
        name = "uk_uoms_company_code", columnNames = {"company_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Uom extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;
}
