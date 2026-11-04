-- WMS-09.1: Bổ sung các cột thông tin và bảng chi tiết dòng hàng cho Đơn mua hàng (Purchase Order - PO)

-- 1. Bổ sung các cột vào bảng purchase_orders
ALTER TABLE purchase_orders
    ADD COLUMN warehouse_id     BIGINT         NULL AFTER supplier_id,
    ADD COLUMN warehouse_name   VARCHAR(100)   NULL AFTER warehouse_id,
    ADD COLUMN subtotal_amount  DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER expected_date,
    ADD COLUMN discount_amount  DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER subtotal_amount,
    ADD COLUMN tax_amount       DECIMAL(18, 2) NOT NULL DEFAULT 0.00 AFTER discount_amount,
    ADD COLUMN approved_by      VARCHAR(50)    NULL AFTER note,
    ADD COLUMN approved_at      DATETIME(6)    NULL AFTER approved_by,
    ADD COLUMN rejected_reason  VARCHAR(500)   NULL AFTER approved_at;

CREATE INDEX idx_purchase_orders_warehouse ON purchase_orders (warehouse_id);

-- 2. Tạo bảng po_items lưu chi tiết dòng hàng của PO
CREATE TABLE po_items (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    po_id            BIGINT         NOT NULL,
    product_id       BIGINT         NULL,
    sku_code         VARCHAR(50)    NOT NULL,
    product_name     VARCHAR(255)   NOT NULL,
    uom              VARCHAR(30)    NULL,
    quantity         DECIMAL(18, 3) NOT NULL,
    unit_price       DECIMAL(18, 2) NOT NULL,
    discount_percent DECIMAL(5, 2)  NOT NULL DEFAULT 0.00,
    discount_amount  DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    tax_rate         DECIMAL(5, 2)  NOT NULL DEFAULT 0.00,
    tax_amount       DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    line_total       DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    note             VARCHAR(255)   NULL,
    created_at       DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at       DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by       VARCHAR(50)    NULL,
    updated_by       VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_po_items_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    INDEX idx_po_items_po (po_id),
    INDEX idx_po_items_sku (sku_code)
) ENGINE = InnoDB;
