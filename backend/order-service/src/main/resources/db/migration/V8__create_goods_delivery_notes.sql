-- WMS-12.1: Phiếu xuất kho (Goods Delivery Note - GDN), luồng SO -> GDN và theo dõi tiến độ xuất hàng (FR17)

-- 1. Bổ sung cột delivered_quantity vào so_items để theo dõi số lượng đã xuất thực tế
ALTER TABLE so_items
    ADD COLUMN delivered_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000 AFTER quantity;

-- 2. Tạo bảng goods_delivery_notes lưu thông tin phiếu xuất kho
CREATE TABLE goods_delivery_notes (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    gdn_number       VARCHAR(30)    NOT NULL,
    so_id            BIGINT         NOT NULL,
    so_number        VARCHAR(30)    NOT NULL,
    customer_id      BIGINT         NOT NULL,
    customer_name    VARCHAR(150)   NULL,
    warehouse_id     BIGINT         NOT NULL,
    warehouse_name   VARCHAR(100)   NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'COMPLETED',
    delivery_date    DATE           NOT NULL,
    total_quantity   DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    total_amount     DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    recipient_name   VARCHAR(100)   NULL,
    recipient_phone  VARCHAR(20)    NULL,
    delivery_address VARCHAR(255)   NULL,
    note             VARCHAR(500)   NULL,
    dispatched_by    VARCHAR(50)    NULL,
    dispatched_at    DATETIME(6)    NULL,
    created_at       DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at       DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by       VARCHAR(50)    NULL,
    updated_by       VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_goods_delivery_notes_number UNIQUE (gdn_number),
    CONSTRAINT fk_goods_delivery_notes_so FOREIGN KEY (so_id) REFERENCES sales_orders (id),
    CONSTRAINT fk_goods_delivery_notes_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    INDEX idx_gdn_so (so_id),
    INDEX idx_gdn_status (status),
    INDEX idx_gdn_warehouse (warehouse_id),
    INDEX idx_gdn_customer (customer_id),
    INDEX idx_gdn_delivery_date (delivery_date)
) ENGINE = InnoDB;

-- 3. Tạo bảng goods_delivery_note_items lưu chi tiết dòng hàng xuất kho
CREATE TABLE goods_delivery_note_items (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    gdn_id             BIGINT         NOT NULL,
    so_item_id         BIGINT         NOT NULL,
    product_id         BIGINT         NOT NULL,
    sku_code           VARCHAR(50)    NOT NULL,
    product_name       VARCHAR(255)   NOT NULL,
    uom                VARCHAR(30)    NULL,
    ordered_quantity   DECIMAL(18, 3) NOT NULL,
    delivered_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    unit_price         DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    line_total         DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    batch_number       VARCHAR(50)    NULL,
    location_id        BIGINT         NULL,
    location_code      VARCHAR(50)    NULL,
    note               VARCHAR(255)   NULL,
    created_at         DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at         DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by         VARCHAR(50)    NULL,
    updated_by         VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_gdn_items_gdn FOREIGN KEY (gdn_id) REFERENCES goods_delivery_notes (id) ON DELETE CASCADE,
    CONSTRAINT fk_gdn_items_so_item FOREIGN KEY (so_item_id) REFERENCES so_items (id),
    INDEX idx_gdn_items_gdn (gdn_id),
    INDEX idx_gdn_items_so_item (so_item_id),
    INDEX idx_gdn_items_sku (sku_code)
) ENGINE = InnoDB;
