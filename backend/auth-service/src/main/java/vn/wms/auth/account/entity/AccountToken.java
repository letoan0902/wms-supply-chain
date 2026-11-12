package vn.wms.auth.account.entity;

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
 * Mã dùng một lần gửi qua email (bảng {@code account_tokens}, migration V8, WMS-23.6).
 *
 * <p>Không lưu mã gốc, chỉ lưu chuỗi hex SHA-256 ({@link #tokenHash}). Mã hợp lệ khi chưa dùng ({@link #usedAt} rỗng)
 * và chưa quá {@link #expiresAt}. Không dùng {@code @TenantId}: người bấm liên kết chưa đăng nhập nên mã được tra
 * theo bản băm trên toàn hệ thống (giống {@code api_keys}).
 */
@Entity
@Table(name = "account_tokens")
@Getter
@Setter
@NoArgsConstructor
public class AccountToken extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Tài khoản nhận liên kết; mã kích hoạt gắn với ADMIN đầu tiên của doanh nghiệp. */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private AccountTokenType type;

    /** SHA-256 của mã, hex chữ thường 64 ký tự, duy nhất toàn hệ thống. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Thời điểm dùng mã, hoặc thời điểm mã bị thay bằng mã mới hơn; {@code null} là chưa dùng. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Mã còn dùng được tại thời điểm {@code now}. */
    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
