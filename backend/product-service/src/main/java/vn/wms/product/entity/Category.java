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

/** Danh mục sản phẩm, thuộc về một doanh nghiệp; mã danh mục duy nhất trong doanh nghiệp. */
@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(
        name = "uk_categories_company_code", columnNames = {"company_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Category extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;
}
