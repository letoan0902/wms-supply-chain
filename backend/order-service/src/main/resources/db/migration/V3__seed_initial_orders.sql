-- ============================================================================
-- Migration V3: Seed dữ liệu ban đầu cho nhà cung cấp, khách hàng và đơn hàng mẫu
-- ============================================================================

-- 1. Seed danh sách nhà cung cấp (Suppliers)
INSERT INTO suppliers (
    id, code, name, tax_code, contact_name, phone, email, address,
    bank_account, bank_name, payment_terms, credit_limit, current_debt,
    active, created_at, updated_at, created_by, updated_by
)
VALUES
    (
        1, 'SUP-COCA', 'Công ty TNHH NGK Coca-Cola Việt Nam', '0301444555', 'Lê Văn Cung Cấp',
        '02838960777', 'contact@cocacola.vn', 'Xa lộ Hà Nội, P. Linh Trung, TP. Thủ Đức, TP. HCM',
        '1122334455', 'Vietcombank - CN Thủ Đức', '30_DAYS', 100000000.00, 25000000.00,
        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    ),
    (
        2, 'SUP-ACECOOK', 'Công ty Cổ phần Acecook Việt Nam', '0300808687', 'Phạm Minh Cung',
        '02838154064', 'info@acecookvietnam.vn', 'KCN Tân Bình, P. Tây Thạnh, Q. Tân Phú, TP. HCM',
        '6677889900', 'BIDV - CN Tân Bình', '15_DAYS', 80000000.00, 12000000.00,
        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    ),
    (
        3, 'SUP-UNILEVER', 'Công ty TNHH Quốc tế Unilever Việt Nam', '0303030303', 'Vũ Đại Lý',
        '02854135686', 'care@unilever.com', 'Tòa nhà Unilever, 156 Nguyễn Lương Bằng, P. Tân Phú, Q.7, TP. HCM',
        '9988776655', 'Techcombank - CN Sài Gòn', '45_DAYS', 200000000.00, 0.00,
        TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
    )
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2. Seed danh sách khách hàng (Customers)
INSERT INTO customers (id, code, name, email, phone, address, active, created_at, updated_at, created_by, updated_by)
VALUES
    (1, 'CUST-WINMART',     'Chuỗi Siêu Thị WinMart Miền Bắc', 'procurement@winmart.vn', '02471066866', 'Số 7 Bằng Lăng 1, KĐT Vinhomes Riverside, Long Biên, Hà Nội', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 'CUST-COOPMART',    'Liên hiệp HTX Thương mại TP.HCM (Saigon Co.op)', 'orders@saigonco-op.com.vn', '02838360143', '199-205 Nguyễn Thái Học, P. Phạm Ngũ Lão, Q.1, TP. HCM', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (3, 'CUST-BACHHOAXANH', 'Công ty CP Thương mại Bách Hóa Xanh', 'bhx_partner@thegioididong.com', '19001908', '128 Trần Quang Khải, P. Tân Định, Q.1, TP. HCM', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 3. Seed đơn mua hàng mẫu (Purchase Orders)
INSERT INTO purchase_orders (id, po_number, supplier_id, status, order_date, expected_date, total_amount, note, created_at, updated_at, created_by, updated_by)
VALUES
    (1, 'PO-2026-0001', 1, 'CONFIRMED', '2026-11-01', '2026-11-05', 50000000.00, 'Đơn đặt hàng nước ngọt Coca-Cola nhập kho Hà Nội', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 'PO-2026-0002', 2, 'DRAFT',     '2026-11-02', '2026-11-08', 30000000.00, 'Đơn đặt hàng mì Hảo Hảo nhập kho Đà Nẵng', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE po_number = VALUES(po_number);

-- 4. Seed đơn bán hàng mẫu (Sales Orders)
INSERT INTO sales_orders (id, so_number, customer_id, status, order_date, expected_date, total_amount, note, created_at, updated_at, created_by, updated_by)
VALUES
    (1, 'SO-2026-0001', 1, 'CONFIRMED', '2026-11-03', '2026-11-06', 25000000.00, 'Xuất hàng giao siêu thị WinMart', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 'SO-2026-0002', 2, 'DRAFT',     '2026-11-04', '2026-11-07', 18000000.00, 'Xuất hàng giao chuỗi Co.opmart', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE so_number = VALUES(so_number);
