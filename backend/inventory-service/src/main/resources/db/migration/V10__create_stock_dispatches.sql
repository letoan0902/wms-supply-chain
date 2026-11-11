-- V10__create_stock_dispatches.sql: Nhật ký sự kiện order.dispatched đã trừ tồn (WMS-12.1)
-- Mỗi phiếu xuất kho (GDN) xác nhận ở order-service phát đúng một sự kiện order.dispatched mang messageId riêng.
-- Sự kiện giao lại (redelivery, restart, phát lại từ dead-letter) gặp ràng buộc duy nhất (company_id, message_id)
-- nên bị bỏ qua, không trừ tồn hai lần.

CREATE TABLE stock_dispatches (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    company_id   BIGINT       NOT NULL,
    message_id   VARCHAR(100) NOT NULL,
    order_id     BIGINT       NULL,
    order_number VARCHAR(30)  NULL,
    warehouse_id BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at   DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by   VARCHAR(50)  NULL,
    updated_by   VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_dispatches_company_message UNIQUE (company_id, message_id),
    INDEX idx_stock_dispatches_company_order (company_id, order_id)
) ENGINE = InnoDB;
