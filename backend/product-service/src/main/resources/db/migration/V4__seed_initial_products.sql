-- ============================================================================
-- Migration V4: Seed dữ liệu ban đầu cho danh mục, sản phẩm và quy đổi đơn vị tính
-- ============================================================================

-- 1. Seed danh mục sản phẩm (Categories)
INSERT INTO categories (id, code, name, description, created_at, updated_at, created_by, updated_by)
VALUES
    (1, 'CAT-DRINK', 'Đồ uống & Nước giải khát', 'Các loại nước ngọt đóng lon, nước tinh khiết, nước ép trái cây', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 'CAT-FOOD',  'Thực phẩm khô & Đóng hộp',  'Mì gói ăn liền, phở ăn liền, bánh kẹo và thực phẩm chế biến sẵn', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (3, 'CAT-HOME',  'Hóa mỹ phẩm & Chăm sóc gia đình', 'Bột giặt, nước xả vải, nước rửa chén, dầu gội', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2. Seed sản phẩm (Products)
INSERT INTO products (
    id, sku, barcode, name, description, category_id, uom_id,
    purchase_price, selling_price, vat_rate, image_url, manage_by_lot, manage_by_expiration,
    safety_stock, reorder_point, max_stock, active, created_at, updated_at, created_by, updated_by
)
VALUES
    (
        1, 'SKU-COCA-330', '8930001001011', 'Nước giải khát Coca-Cola Lon 330ml',
        'Nước có ga hương vị truyền thống thơm ngon, sảng khoái', 1, (SELECT id FROM uoms WHERE code = 'CAN' LIMIT 1),
        8500.00, 10000.00, 10.00, NULL, TRUE, TRUE,
        50.000, 100.000, 1000.000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    ),
    (
        2, 'SKU-AQUAFINA-500', '8930001002018', 'Nước khoáng tinh khiết Aquafina 500ml',
        'Nước uống đóng chai tiệt trùng công nghệ lọc RO', 1, (SELECT id FROM uoms WHERE code = 'BOTTLE' LIMIT 1),
        4500.00, 6000.00, 10.00, NULL, TRUE, TRUE,
        100.000, 200.000, 2000.000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    ),
    (
        3, 'SKU-HAOHAO-TOMCHUA', '8930001003015', 'Mì ăn liền Hảo Hảo Tôm Chua Cay 75g',
        'Mì tôm chua cay hương vị quốc dân của Acecook', 2, (SELECT id FROM uoms WHERE code = 'PACK' LIMIT 1),
        3800.00, 4500.00, 8.00, NULL, TRUE, TRUE,
        200.000, 500.000, 5000.000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    ),
    (
        4, 'SKU-OMACHI-SOTBO', '8930001004012', 'Mì khoai tây Omachi Xốt Bò Hầm 80g',
        'Sợi mì từ khoai tây dai ngon hòa quyện xốt bò hầm', 2, (SELECT id FROM uoms WHERE code = 'PACK' LIMIT 1),
        7200.00, 9000.00, 8.00, NULL, TRUE, TRUE,
        150.000, 300.000, 3000.000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    ),
    (
        5, 'SKU-OMO-COMFORT-2KG', '8930001005019', 'Nước giặt OMO Matic Comfort Túi 2.0kg',
        'Nước giặt chuyên dụng cho máy giặt cửa trên và cửa trước hương hoa thơm ngát', 3, (SELECT id FROM uoms WHERE code = 'BAG' LIMIT 1),
        125000.00, 155000.00, 10.00, NULL, FALSE, FALSE,
        30.000, 60.000, 500.000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    )
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 3. Seed quy đổi đơn vị tính (Unit Conversions)
-- Thùng Coca (24 lon)
INSERT INTO unit_conversions (product_id, from_uom_id, to_uom_id, conversion_factor, barcode, description, created_at, updated_at, created_by, updated_by)
VALUES
    (
        1, (SELECT id FROM uoms WHERE code = 'CTN' LIMIT 1), (SELECT id FROM uoms WHERE code = 'CAN' LIMIT 1),
        24.000000, '8930001001028', '1 Thùng = 24 Lon', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    ),
    -- Thùng Mì Hảo Hảo (30 gói)
    (
        3, (SELECT id FROM uoms WHERE code = 'CTN' LIMIT 1), (SELECT id FROM uoms WHERE code = 'PACK' LIMIT 1),
        30.000000, '8930001003022', '1 Thùng = 30 Gói', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    )
ON DUPLICATE KEY UPDATE conversion_factor = VALUES(conversion_factor);
