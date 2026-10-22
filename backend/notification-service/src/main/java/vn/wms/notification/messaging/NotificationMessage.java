package vn.wms.notification.messaging;

/**
 * Nội dung sự kiện cảnh báo nhận từ RabbitMQ (JSON).
 *
 * @param eventType loại sự kiện, ví dụ LOW_STOCK
 * @param recipient địa chỉ email người nhận
 * @param subject   tiêu đề email
 * @param content   nội dung email
 */
public record NotificationMessage(String eventType, String recipient, String subject, String content) {
}
