package vn.wms.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Lớp cha cho mọi entity nghiệp vụ, tự động ghi bốn cột audit theo quy ước của dự án:
 * created_at, updated_at, created_by, updated_by. Thời gian lưu theo UTC.
 *
 * <p>Người thực hiện (created_by, updated_by) lấy từ header X-User-Name do api-gateway chuyển tiếp,
 * không có thì ghi "system" (xem {@link RequestHeaderAuditorAware}). Auditing được bật tự động
 * bởi WmsCommonAutoConfiguration khi service dùng Spring Data JPA.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
