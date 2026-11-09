package vn.wms.common.tenant.sample;

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

/** Entity mẫu: mã duy nhất trong một doanh nghiệp, đúng mẫu mà các service áp dụng cho bảng nghiệp vụ. */
@Entity
@Table(name = "sample_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_sample_items_company_code", columnNames = {"company_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class SampleItem extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    public SampleItem(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
