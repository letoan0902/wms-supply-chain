package vn.wms.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.wms.common.tenant.TenantEntity;

/**
 * Nhật ký các thông báo đã xử lý (phục vụ tra cứu và gửi lại khi lỗi). Thuộc về doanh nghiệp phát sinh sự kiện
 * (WMS-22.4c): {@code company_id} do {@link TenantEntity} tự gán khi lưu và tự lọc khi truy vấn.
 */
@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notification_logs_company_status", columnList = "company_id, status"),
        @Index(name = "idx_notification_logs_company_created", columnList = "company_id, created_at")})
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    /** Lưu dạng chuỗi VARCHAR (không dùng kiểu ENUM native của MySQL). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
