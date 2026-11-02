-- ============================================================================
-- Migration V2: Bổ sung các thuộc tính SKU theo yêu cầu WMS-07.1
-- Giá nhập/bán, VAT, ảnh sản phẩm, cờ quản lý Lô & Hạn sử dụng, định mức tồn kho (Safety Stock, Reorder Point, Max Stock)
-- ============================================================================

ALTER TABLE products
    ADD COLUMN purchase_price        DECIMAL(18, 2) NULL DEFAULT 0.00 AFTER description,
    ADD COLUMN selling_price         DECIMAL(18, 2) NULL DEFAULT 0.00 AFTER purchase_price,
    ADD COLUMN vat_rate              DECIMAL(5, 2)  NULL DEFAULT 0.00 AFTER selling_price,
    ADD COLUMN image_url             VARCHAR(500)   NULL AFTER vat_rate,
    ADD COLUMN manage_by_lot         BOOLEAN        NOT NULL DEFAULT FALSE AFTER image_url,
    ADD COLUMN manage_by_expiration  BOOLEAN        NOT NULL DEFAULT FALSE AFTER manage_by_lot,
    ADD COLUMN safety_stock          DECIMAL(18, 3) NULL DEFAULT 0.000 AFTER manage_by_expiration,
    ADD COLUMN reorder_point         DECIMAL(18, 3) NULL DEFAULT 0.000 AFTER safety_stock,
    ADD COLUMN max_stock             DECIMAL(18, 3) NULL DEFAULT 0.000 AFTER reorder_point;
