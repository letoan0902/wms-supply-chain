-- V5__multi_tenant_company_id.sql: Phân tách dữ liệu theo doanh nghiệp (Multi-tenant) cho inventory-service (WMS-22.4a)
-- Thêm cột company_id cho các bảng nghiệp vụ, gán dữ liệu hiện có về Doanh nghiệp mẫu A (company_id = 1),
-- đổi ràng buộc duy nhất mã kho và mã vị trí theo (company_id, code),
-- và bổ sung các index bắt đầu bằng company_id cho các cột hay lọc.

-- 1. Bảng kho hàng (warehouses)
ALTER TABLE warehouses ADD COLUMN company_id BIGINT NULL;
UPDATE warehouses SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE warehouses MODIFY company_id BIGINT NOT NULL;

ALTER TABLE warehouses DROP INDEX uk_warehouses_code;
ALTER TABLE warehouses ADD CONSTRAINT uk_warehouses_company_code UNIQUE (company_id, code);
CREATE INDEX idx_warehouses_company ON warehouses (company_id);

-- 2. Bảng vị trí ô/kệ (bin_locations)
ALTER TABLE bin_locations ADD COLUMN company_id BIGINT NULL;
UPDATE bin_locations b JOIN warehouses w ON b.warehouse_id = w.id SET b.company_id = w.company_id WHERE b.company_id IS NULL;
UPDATE bin_locations SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE bin_locations MODIFY company_id BIGINT NOT NULL;

ALTER TABLE bin_locations DROP INDEX uk_bin_locations_code;
ALTER TABLE bin_locations ADD CONSTRAINT uk_bin_locations_company_code UNIQUE (company_id, code);
CREATE INDEX idx_bin_locations_company ON bin_locations (company_id);

-- 3. Bảng tồn kho (stock_items)
ALTER TABLE stock_items ADD COLUMN company_id BIGINT NULL;
UPDATE stock_items s JOIN bin_locations b ON s.bin_location_id = b.id SET s.company_id = b.company_id WHERE s.company_id IS NULL;
UPDATE stock_items SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE stock_items MODIFY company_id BIGINT NOT NULL;

CREATE INDEX idx_stock_items_company ON stock_items (company_id);
CREATE INDEX idx_stock_items_company_product ON stock_items (company_id, product_id);

-- 4. Bảng giữ hàng ảo (stock_reservations)
ALTER TABLE stock_reservations ADD COLUMN company_id BIGINT NULL;
UPDATE stock_reservations r JOIN stock_items s ON r.stock_item_id = s.id SET r.company_id = s.company_id WHERE r.company_id IS NULL;
UPDATE stock_reservations SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE stock_reservations MODIFY company_id BIGINT NOT NULL;

CREATE INDEX idx_stock_reservations_company ON stock_reservations (company_id);

-- 5. Bảng hàng đang trên đường về kho (incoming_stocks)
ALTER TABLE incoming_stocks ADD COLUMN company_id BIGINT NULL;
UPDATE incoming_stocks i JOIN warehouses w ON i.warehouse_id = w.id SET i.company_id = w.company_id WHERE i.company_id IS NULL;
UPDATE incoming_stocks SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE incoming_stocks MODIFY company_id BIGINT NOT NULL;

CREATE INDEX idx_incoming_stocks_company ON incoming_stocks (company_id);

