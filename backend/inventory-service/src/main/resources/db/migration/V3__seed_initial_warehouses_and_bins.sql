-- ============================================================================
-- Migration V3: Seed dữ liệu ban đầu cho kho hàng, vị trí ô/kệ và tồn kho mẫu
-- Đảm bảo khớp với phân quyền user trong auth-service (Kho ID 1 và 2)
-- ============================================================================

-- 1. Seed danh sách kho bãi tiêu chuẩn
INSERT INTO warehouses (id, code, name, address, manager_name, contact_phone, capacity, active, created_at, updated_at, created_by, updated_by)
VALUES
    (1, 'WH-HN-01', 'Kho Tổng Hà Nội', 'Khu công nghiệp Đài Tư, P. Phúc Lợi, Q. Long Biên, Hà Nội', 'Nguyễn Văn Quản Lý', '0901234567', 5000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 'WH-DN-01', 'Kho Miền Trung Đà Nẵng', 'Khu công nghiệp Hòa Khánh, Q. Liên Chiểu, TP. Đà Nẵng', 'Nguyễn Văn Quản Lý', '0901234568', 3000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (3, 'WH-HCM-01', 'Kho Miền Nam Hồ Chí Minh', 'Khu công nghệ cao TP. Thủ Đức, TP. Hồ Chí Minh', 'Trần Quản Lý HCM', '0901234569', 8000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2. Seed các vị trí ô/kệ (Bin Locations) mẫu cho Kho Tổng Hà Nội (id = 1)
INSERT INTO bin_locations (id, warehouse_id, zone, rack, bin, code, max_weight, status, active, created_at, updated_at, created_by, updated_by)
VALUES
    -- Khu vực tiếp nhận hàng (RECEIVING)
    (1, 1, 'RECEIVING', 'R00', 'B01', 'WH-HN-01-RECEIVING-R00-B01', 5000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 1, 'RECEIVING', 'R00', 'B02', 'WH-HN-01-RECEIVING-R00-B02', 5000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),

    -- Khu vực lưu trữ Zone A (Kệ R01, Ô B01 -> B03)
    (3, 1, 'A', 'R01', 'B01', 'WH-HN-01-A-R01-B01', 1000.00, 'OCCUPIED', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (4, 1, 'A', 'R01', 'B02', 'WH-HN-01-A-R01-B02', 1000.00, 'OCCUPIED', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (5, 1, 'A', 'R01', 'B03', 'WH-HN-01-A-R01-B03', 1000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),

    -- Khu vực lưu trữ Zone B (Kệ R01, Ô B01 -> B02)
    (6, 1, 'B', 'R01', 'B01', 'WH-HN-01-B-R01-B01', 1200.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (7, 1, 'B', 'R01', 'B02', 'WH-HN-01-B-R01-B02', 1200.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),

    -- Khu vực đóng gói xuất hàng (PACKING)
    (8, 1, 'PACKING', 'R00', 'B01', 'WH-HN-01-PACKING-R00-B01', 3000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),

    -- Các vị trí mẫu cho Kho Miền Trung Đà Nẵng (id = 2)
    (9,  2, 'RECEIVING', 'R00', 'B01', 'WH-DN-01-RECEIVING-R00-B01', 5000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (10, 2, 'A',         'R01', 'B01', 'WH-DN-01-A-R01-B01',         1000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (11, 2, 'PACKING',   'R00', 'B01', 'WH-DN-01-PACKING-R00-B01',   3000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),

    -- Các vị trí mẫu cho Kho Miền Nam TP.HCM (id = 3)
    (12, 3, 'RECEIVING', 'R00', 'B01', 'WH-HCM-01-RECEIVING-R00-B01', 5000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (13, 3, 'A',         'R01', 'B01', 'WH-HCM-01-A-R01-B01',         1000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (14, 3, 'PACKING',   'R00', 'B01', 'WH-HCM-01-PACKING-R00-B01',   3000.00, 'EMPTY', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE code = VALUES(code);

-- 3. Seed hàng tồn kho mẫu (Stock Items) cho Kho 1
INSERT INTO stock_items (id, product_id, bin_location_id, lot_no, quantity, reserved_quantity, created_at, updated_at, created_by, updated_by)
VALUES
    (1, 1, 3, 'LOT-202611-01', 120.000, 0.000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 2, 4, 'LOT-202611-02', 250.000, 0.000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);
