package vn.wms.auth.apikey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.wms.common.persistence.AuditableEntity;

import java.time.Instant;

/**
 * API key của một doanh nghiệp cho hệ thống ngoài (bảng {@code api_keys}, migration V7, WMS-25.1).
 *
 * <p>Không lưu key gốc: chỉ lưu 8 ký tự đầu ({@link #keyPrefix}) để nhận diện và chuỗi hex SHA-256 ({@link #keyHash})
 * để api-gateway tra cứu. Không dùng {@code @TenantId}: auth-service lọc tường minh theo {@code companyId} của người
 * đang đăng nhập, còn tác vụ định kỳ đồng bộ Redis đọc key của mọi doanh nghiệp.
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Tên do ADMIN đặt để nhớ key dùng cho hệ thống nào, ví dụ "Website bán hàng". */
    @Column(nullable = false, length = 100)
    private String name;

    /** 8 ký tự đầu của key, ví dụ {@code wms_Ab3x}. */
    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    /** SHA-256 của toàn bộ key, dạng hex chữ thường 64 ký tự, duy nhất toàn hệ thống. */
    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    /** Lần dùng gần nhất, cập nhật định kỳ từ Redis nên có thể trễ tối đa một chu kỳ đổ dữ liệu. */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /** Thời điểm thu hồi; {@code null} là key đang hoạt động. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** ID tài khoản ADMIN đã tạo key. */
    @Column(name = "created_by_user")
    private Long createdByUser;

    /** Key chưa bị thu hồi. */
    public boolean isActive() {
        return revokedAt == null;
    }
}
