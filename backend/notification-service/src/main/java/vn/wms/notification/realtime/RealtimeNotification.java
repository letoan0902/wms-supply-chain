package vn.wms.notification.realtime;

import java.time.Instant;

/**
 * Phong bì JSON của mọi tin đẩy qua WebSocket (hợp đồng với frontend, docs/MESSAGING.md mục 8.3).
 *
 * @param id         messageId của sự kiện RabbitMQ gốc (UUID), không có thì sinh UUID mới; frontend dùng để bỏ tin trùng
 * @param type       loại tin, quyết định topic
 * @param severity   mức độ hiển thị
 * @param title      tiêu đề tiếng Việt, sẵn sàng hiển thị toast
 * @param message    nội dung tiếng Việt
 * @param occurredAt thời điểm sự kiện xảy ra (UTC, ISO 8601)
 * @param payload    nguyên record sự kiện gốc, hoặc {@link KpiSnapshot} với {@link RealtimeEventType#KPI_UPDATED}
 */
public record RealtimeNotification(String id, RealtimeEventType type, NotificationSeverity severity, String title,
                                   String message, Instant occurredAt, Object payload) {
}
