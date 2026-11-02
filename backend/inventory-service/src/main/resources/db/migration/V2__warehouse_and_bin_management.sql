-- V2__warehouse_and_bin_management.sql: Cập nhật thông tin chi tiết kho bãi và vị trí ô/kệ (WMS-05, WMS-06)

ALTER TABLE warehouses
    ADD COLUMN manager_name VARCHAR(100) NULL AFTER address,
    ADD COLUMN contact_phone VARCHAR(30) NULL AFTER manager_name,
    ADD COLUMN capacity INT NOT NULL DEFAULT 0 AFTER contact_phone;

ALTER TABLE bin_locations
    ADD COLUMN max_weight DECIMAL(10, 2) NULL DEFAULT 1000.00 AFTER code,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'EMPTY' AFTER max_weight;

CREATE INDEX idx_bin_locations_warehouse_status ON bin_locations (warehouse_id, status);
CREATE INDEX idx_bin_locations_zone_rack ON bin_locations (warehouse_id, zone, rack);

