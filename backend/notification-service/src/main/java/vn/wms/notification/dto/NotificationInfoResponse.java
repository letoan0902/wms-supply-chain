package vn.wms.notification.dto;

/**
 * Thông tin cấu hình đang chạy của notification-service, dùng để kiểm tra nhanh khi tích hợp.
 *
 * @param service    tên service
 * @param exchange   exchange RabbitMQ đang lắng nghe
 * @param queue      queue nhận sự kiện
 * @param routingKey routing key binding giữa exchange và queue
 * @param mailHost   máy chủ SMTP đang cấu hình
 * @param mailFrom   địa chỉ người gửi
 */
public record NotificationInfoResponse(String service, String exchange, String queue, String routingKey,
                                       String mailHost, String mailFrom) {
}
