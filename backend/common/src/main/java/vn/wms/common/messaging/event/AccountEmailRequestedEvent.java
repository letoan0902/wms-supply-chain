package vn.wms.common.messaging.event;

import java.time.Instant;

/**
 * Sự kiện {@code auth.activation-requested} và {@code auth.password-reset-requested} (WMS-23.6): auth-service yêu cầu
 * notification-service gửi một email chứa liên kết dùng một lần. auth-service không gửi mail trực tiếp.
 *
 * <p>Trường {@link #link} chứa mã bí mật: không ghi log nội dung sự kiện ({@link #toString()} đã che liên kết).
 * Chỉ notification-service ghi liên kết ra log khi máy chủ chưa cấu hình SMTP để nhóm thử được luồng.
 *
 * @param kind           loại email: kích hoạt doanh nghiệp hoặc đặt lại mật khẩu
 * @param companyId      doanh nghiệp của người nhận (trùng header {@code x-wms-company-id})
 * @param companyName    tên doanh nghiệp, hiển thị trong nội dung email
 * @param recipientEmail địa chỉ nhận
 * @param recipientName  họ tên người nhận, có thể rỗng
 * @param link           liên kết đầy đủ tới trang {@code /activate} hoặc {@code /reset-password} của giao diện
 * @param expiresAt      thời điểm liên kết hết hiệu lực
 * @param requestedAt    thời điểm phát yêu cầu
 */
public record AccountEmailRequestedEvent(AccountEmailKind kind, Long companyId, String companyName,
                                         String recipientEmail, String recipientName, String link,
                                         Instant expiresAt, Instant requestedAt) {

    /** Không bao giờ in liên kết (chứa mã dùng một lần) khi đối tượng bị ghi log. */
    @Override
    public String toString() {
        return "AccountEmailRequestedEvent[kind=" + kind + ", companyId=" + companyId
                + ", recipientEmail=" + recipientEmail + ", link=***, expiresAt=" + expiresAt
                + ", requestedAt=" + requestedAt + "]";
    }
}
