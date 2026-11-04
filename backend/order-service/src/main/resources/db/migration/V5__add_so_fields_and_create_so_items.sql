-- WMS-11.1: Bổ sung các cột thông tin và bảng chi tiết dòng hàng cho Đơn bán hàng (Sales Order - SO) & Tồn khả dụng (ATP)

-- 1. Bổ sung các cột vào bảng sales_orders
ALTER TABLE sales_orders
    ADD COLUMN warehouse_id      BIGINT         NULL AFTER customer_id,
    ADD COLUMN warehouse_name    VARCHAR(100)   NULL AFTER warehouse_id,
    ADD COLUMN delivery_address  VARCHAR(255)   NULL AFTER warehouse_name,
    ADD COLUMN recipient_name    VARCHAR(150)   NULL AFTER delivery_address,
    ADD COLUMN recipient_phone   VARCHAR(30)    NULL AFTER recipient_name,
    ADD COLUMN subtotal_amount   DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER expected_date,
    ADD COLUMN discount_amount   DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER subtotal_amount,
    ADD COLUMN tax_amount        DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER discount_amount,
    ADD COLUMN confirmed_by      VARCHAR(50)    NULL AFTER note,
    ADD COLUMN confirmed_at      DATETIME(6)    NULL AFTER confirmed_by,
    ADD COLUMN backorder_reason  VARCHAR(500)   NULL AFTER confirmed_at;

CREATE INDEX idx_sales_orders_warehouse ON sales_orders (warehouse_id);

-- 2. Tạo bảng so_items lưu chi tiết dòng hàng của SO kèm thông tin tồn khả dụng ATP (FR15)
CREATE TABLE so_items (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    so_id             BIGINT         NOT NULL,
    product_id        BIGINT         NULL,
    sku_code          VARCHAR(50)    NOT NULL,
    product_name      VARCHAR(255)   NOT NULL,
    uom               VARCHAR(30)    NULL,
    quantity          DECIMAL(18, 3) NOT NULL,
    unit_price        DECIMAL(18, 2) NOT NULL,
    discount_percent  DECIMAL(5, 2)  NOT NULL DEFAULT 0.00,
    discount_amount   DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    tax_rate          DECIMAL(5, 2)  NOT NULL DEFAULT 0.00,
    tax_amount        DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    line_total        DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    on_hand_quantity  DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    reserved_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    atp_quantity      DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    is_backorder      BOOLEAN        NOT NULL DEFAULT FALSE,
    note              VARCHAR(255)   NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by        VARCHAR(50)    NULL,
    updated_by        VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_so_items_so FOREIGN KEY (so_id) REFERENCES sales_orders (id) ON DELETE CASCADE,
    INDEX idx_so_items_so (so_id),
    INDEX idx_so_items_sku (sku_code)
) ENGINE = InnoDB;
