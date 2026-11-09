-- V5__add_lot_fields_and_lock_support.sql: Quản lý Lô (Batch/Lot), Hạn sử dụng (Exp Date) & Khóa lô hết hạn (WMS-10.2 - FR12)

-- 1. Tạo bảng inventory_lots lưu trữ chi tiết từng Lô hàng (Số lô, Ngày SX, Hạn dùng) theo Vị trí ô/kệ (Bin ID)
CREATE TABLE inventory_lots (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    lot_number        VARCHAR(50)    NOT NULL,
    product_id        BIGINT         NOT NULL,
    bin_location_id   BIGINT         NOT NULL,
    mfg_date          DATE           NULL,
    exp_date          DATE           NULL,
    quantity          DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    reserved_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    is_locked         BOOLEAN        NOT NULL DEFAULT FALSE,
    locked_reason     VARCHAR(255)   NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by        VARCHAR(50)    NULL,
    updated_by        VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inventory_lots_bin FOREIGN KEY (bin_location_id) REFERENCES bin_locations (id),
    INDEX idx_inventory_lots_product (product_id),
    INDEX idx_inventory_lots_bin (bin_location_id),
    INDEX idx_inventory_lots_number (lot_number),
    INDEX idx_inventory_lots_exp_date (exp_date),
    INDEX idx_inventory_lots_locked (is_locked)
) ENGINE = InnoDB;

-- 2. Đồng thời bổ sung mfg_date, exp_date và khóa lô vào stock_items để phục vụ xuất kho FEFO/FIFO
ALTER TABLE stock_items
    ADD COLUMN mfg_date DATE NULL AFTER lot_no,
    ADD COLUMN exp_date DATE NULL AFTER mfg_date,
    ADD COLUMN is_locked BOOLEAN NOT NULL DEFAULT FALSE AFTER reserved_quantity,
    ADD COLUMN locked_reason VARCHAR(255) NULL AFTER is_locked;

CREATE INDEX idx_stock_items_exp_date ON stock_items (exp_date);
CREATE INDEX idx_stock_items_lot_no ON stock_items (lot_no);
CREATE INDEX idx_stock_items_locked ON stock_items (is_locked);
