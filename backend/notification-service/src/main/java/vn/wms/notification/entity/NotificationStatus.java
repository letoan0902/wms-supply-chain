package vn.wms.notification.entity;

/** Kết quả gửi thông báo. */
public enum NotificationStatus {
    SENT,
    FAILED,
    /** SMTP chưa cấu hình (MAIL_HOST rỗng): không gửi, nội dung email được ghi vào log ứng dụng để nhóm kiểm tra. */
    LOGGED
}
