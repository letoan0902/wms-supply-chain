-- V3__create_stock_reservations.sql: Bảng lưu thông tin giữ hàng ảo (Soft Reservation) khi duyệt đơn bán SO (WMS-11.2 - FR16)

CREATE TABLE stock_reservations (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    order_id          BIGINT         NOT NULL,
    order_number      VARCHAR(30)    NOT NULL,
    product_id        BIGINT         NOT NULL,
    stock_item_id     BIGINT         NOT NULL,
    warehouse_id      BIGINT         NULL,
    reserved_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    message_id        VARCHAR(100)   NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by        VARCHAR(50)    NULL,
    updated_by        VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservations_stock_item FOREIGN KEY (stock_item_id) REFERENCES stock_items (id) ON DELETE CASCADE,
    INDEX idx_reservations_order (order_id),
    INDEX idx_reservations_product (product_id),
    INDEX idx_reservations_stock_item (stock_item_id),
    INDEX idx_reservations_status (status)
) ENGINE = InnoDB;
