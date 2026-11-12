-- V3__stock_alert_notification_logs.sql: nhật ký cảnh báo tồn kho (WMS-15.2).
-- notification_logs ghi thêm hai loại dòng: bản tổng hợp đẩy lên chuông thông báo (channel WEBSOCKET, recipient là kênh
-- STOMP) và email cảnh báo (channel EMAIL). dedup_key chống ghi và gửi trùng khi tin được giao lại hoặc phát lại:
--   SUMMARY:<loại>:<ngày>:<kho>      một dòng cho mỗi bản tổng hợp
--   EMAIL:<loại>:<ngày>:<email>      một email cho mỗi người nhận, loại và ngày
-- Dòng cũ (email của luồng alert.* trước đây) có channel EMAIL và dedup_key NULL; MySQL cho phép nhiều NULL trong
-- ràng buộc duy nhất. status có thêm giá trị LOGGED (SMTP chưa cấu hình, chỉ ghi nội dung vào log).

ALTER TABLE notification_logs
    ADD COLUMN channel      VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN alert_kind   VARCHAR(20)  NULL,
    ADD COLUMN alert_date   DATE         NULL,
    ADD COLUMN warehouse_id BIGINT       NULL,
    ADD COLUMN dedup_key    VARCHAR(191) NULL;

ALTER TABLE notification_logs
    ADD CONSTRAINT uk_notification_logs_company_dedup UNIQUE (company_id, dedup_key);
