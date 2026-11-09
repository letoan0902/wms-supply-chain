package vn.wms.common.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import org.hibernate.annotations.TenantId;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.persistence.AuditableEntity;

/**
 * Lớp cha cho mọi entity nghiệp vụ thuộc về một doanh nghiệp. Thêm cột {@code company_id} và giao cho Hibernate
 * ({@link TenantId}) tự làm hai việc:
 * <ul>
 *   <li>gán {@code company_id} bằng doanh nghiệp trong {@link TenantContext} khi thêm mới;</li>
 *   <li>thêm điều kiện {@code company_id = ?} vào mọi truy vấn JPQL, Criteria và phương thức truy vấn của
 *       repository, nên bản ghi của doanh nghiệp khác không bao giờ xuất hiện trong kết quả.</li>
 * </ul>
 *
 * <p>Hibernate KHÔNG thêm điều kiện đó khi tải theo khóa chính ({@code findById}, {@code EntityManager.find},
 * {@code getReferenceById}, merge một đối tượng có sẵn ID). Lớp này bù lại bằng {@link #verifyCompany()}: bản ghi
 * vừa tải mà thuộc doanh nghiệp khác thì ném {@link ApiException} mã {@link ErrorCode#NOT_FOUND}, tức là
 * API trả 404 như thể bản ghi không tồn tại. Hệ quả cần nhớ: {@code findById} với ID của doanh nghiệp khác
 * ném lỗi 404 chứ không trả {@code Optional.empty()}; muốn kiểm tra tồn tại thì dùng {@code existsById}.
 *
 * <p>Không có setter cho {@code companyId}: giá trị không bao giờ lấy từ dữ liệu do client gửi.
 * Truy vấn SQL gốc (native query) KHÔNG được lọc tự động, phải tự thêm điều kiện {@code company_id}.
 */
@MappedSuperclass
@Getter
public abstract class TenantEntity extends AuditableEntity {

    @TenantId
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    /** Chặn việc thêm bản ghi khi chưa xác định doanh nghiệp, tránh sinh dòng mồ côi với company_id = 0. */
    @PrePersist
    void requireCompany() {
        if (!TenantContext.isSet()) {
            throw new ApiException(ErrorCode.COMPANY_REQUIRED,
                    "Không thể lưu dữ liệu khi chưa xác định doanh nghiệp");
        }
    }

    /**
     * Chặn việc đọc bản ghi của doanh nghiệp khác qua đường tải theo khóa chính (đường Hibernate không tự lọc).
     * Với kết quả của truy vấn đã được lọc thì điều kiện này luôn thỏa.
     */
    @PostLoad
    void verifyCompany() {
        if (companyId != null && !companyId.equals(TenantContext.getCompanyId())) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
