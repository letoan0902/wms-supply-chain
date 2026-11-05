-- V4__create_incoming_stocks.sql: Quản lý chỉ tiêu hàng đang trên đường về kho (Incoming Stock - WMS-09.2)
-- Khi Đơn mua hàng (PO) được duyệt (APPROVED), hệ thống tự động ghi nhận lượng hàng chờ nhập vào kho tương ứng.

CREATE TABLE incoming_stocks (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    warehouse_id    BIGINT         NOT NULL,
    product_id      BIGINT         NOT NULL,
    sku_code        VARCHAR(64)    NOT NULL,
    product_name    VARCHAR(255)   NULL,
    order_id        BIGINT         NOT NULL,
    order_number    VARCHAR(64)    NOT NULL,
    quantity        DECIMAL(18, 3) NOT NULL DEFAULT 0,
    uom             VARCHAR(20)    NULL,
    status          VARCHAR(30)    NOT NULL DEFAULT 'INCOMING',
    message_id      VARCHAR(100)   NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at      DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by      VARCHAR(50)    NULL,
    updated_by      VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_incoming_stocks_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT uk_incoming_order_product UNIQUE (order_id, product_id),
    INDEX idx_incoming_warehouse_product (warehouse_id, product_id),
    INDEX idx_incoming_order (order_id),
    INDEX idx_incoming_status (status)
) ENGINE = InnoDB;

