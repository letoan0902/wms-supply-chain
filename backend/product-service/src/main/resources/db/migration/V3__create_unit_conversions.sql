-- ============================================================================
-- Migration V3: Bảng unit_conversions quản lý quy đổi đơn vị tính đa cấp (WMS-07.2)
-- Thêm một số đơn vị tính tiêu chuẩn nếu chưa có
-- ============================================================================

-- 1. Bổ sung các đơn vị tính cơ sở và đóng gói phổ biến vào bảng uoms
INSERT IGNORE INTO uoms (code, name, created_at, updated_at, created_by, updated_by) VALUES
    ('PCS',    'Cái',      UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('BOTTLE', 'Chai',     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('CAN',    'Lon',      UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('PACK',   'Lốc/Gói',  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('BOX',    'Hộp',      UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('CTN',    'Thùng',    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('PLT',    'Pallet',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('KG',     'Kilogram', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    ('BAG',    'Bao/Túi',  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system');

-- 2. Bảng unit_conversions: Lưu tỷ lệ quy đổi giữa các đơn vị tính theo từng SKU
CREATE TABLE unit_conversions (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    product_id        BIGINT         NOT NULL,
    from_uom_id       BIGINT         NOT NULL,
    to_uom_id         BIGINT         NOT NULL,
    conversion_factor DECIMAL(18, 6) NOT NULL,
    barcode           VARCHAR(64)    NULL,
    description       VARCHAR(255)   NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by        VARCHAR(50)    NULL,
    updated_by        VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_from_to_uom UNIQUE (product_id, from_uom_id, to_uom_id),
    CONSTRAINT fk_unit_conversions_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_unit_conversions_from_uom FOREIGN KEY (from_uom_id) REFERENCES uoms (id),
    CONSTRAINT fk_unit_conversions_to_uom FOREIGN KEY (to_uom_id) REFERENCES uoms (id),
    INDEX idx_unit_conversions_product (product_id)
) ENGINE = InnoDB;
