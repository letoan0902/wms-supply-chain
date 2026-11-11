package vn.wms.inventory.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.tenant.TenantEntity;

import java.time.LocalDate;

/**
 * Dấu chống gửi trùng của tác vụ quét cảnh báo (WMS-15.1): mỗi (doanh nghiệp, ngày, loại cảnh báo) chỉ có một dòng.
 * Dòng được thêm TRƯỚC khi phát sự kiện, nên lần chạy thứ hai trong ngày (bấm quét tay, hai instance cùng chạy lúc
 * 00:00) gặp ràng buộc duy nhất và không phát lại.
 */
@Entity
@Table(name = "stock_alert_runs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_alert_runs_company_date_kind",
                columnNames = {"company_id", "alert_date", "alert_kind"})
})
@Getter
@Setter
@NoArgsConstructor
public class StockAlertRun extends TenantEntity {

    /** Đang phát sự kiện. */
    public static final String STATUS_RUNNING = "RUNNING";
    /** Đã phát xong. */
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_date", nullable = false)
    private LocalDate alertDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "alert_kind", nullable = false, length = 20)
    private StockAlertKind alertKind;

    @Column(nullable = false, length = 20)
    private String status = STATUS_RUNNING;

    /** Số kho có cảnh báo (số bản tổng hợp đã phát). */
    @Column(name = "warehouse_count", nullable = false)
    private int warehouseCount;

    /** Tổng số dòng cảnh báo. */
    @Column(name = "item_count", nullable = false)
    private int itemCount;

    /** Số email cảnh báo đã yêu cầu notification-service gửi. */
    @Column(name = "email_count", nullable = false)
    private int emailCount;

    /** SCHEDULER hoặc tên đăng nhập của người bấm quét tay. */
    @Column(name = "triggered_by", length = 50)
    private String triggeredBy;

    @Column(length = 500)
    private String note;

    public StockAlertRun(LocalDate alertDate, StockAlertKind alertKind, String triggeredBy) {
        this.alertDate = alertDate;
        this.alertKind = alertKind;
        this.triggeredBy = triggeredBy;
    }
}
