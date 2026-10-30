-- V2__seed_default_accounts.sql
-- Seed dữ liệu tài khoản mặc định cho 6 vai trò của hệ thống WMS và tài khoản demo phục vụ kiểm thử.
-- Mật khẩu mặc định của tất cả tài khoản: Demopassword
-- BCrypt hash: $2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2

-- 1. Chèn danh sách 6 tài khoản nghiệp vụ + 1 tài khoản demo kiểm thử
INSERT INTO users (username, email, password_hash, full_name, enabled, created_by, updated_by) VALUES
    ('admin', 'admin@wms.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'System Administrator', TRUE, 'system', 'system'),
    ('manager', 'manager@wms.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Warehouse Manager', TRUE, 'system', 'system'),
    ('staff', 'staff@wms.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Warehouse Staff', TRUE, 'system', 'system'),
    ('purchasing', 'purchasing@wms.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Purchasing Staff', TRUE, 'system', 'system'),
    ('sales', 'sales@wms.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Sales Staff', TRUE, 'system', 'system'),
    ('accountant', 'accountant@wms.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Accountant', TRUE, 'system', 'system'),
    ('demo', 'demo@example.com', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Demo User', TRUE, 'system', 'system')
ON DUPLICATE KEY UPDATE updated_at = UTC_TIMESTAMP(6);

-- 2. Gán quyền tương ứng vào bảng user_roles (dùng INSERT IGNORE để idempotent, chạy lại không lỗi)
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.name = 'ADMIN';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'manager' AND r.name = 'WAREHOUSE_MANAGER';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'staff' AND r.name = 'WAREHOUSE_STAFF';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'purchasing' AND r.name = 'PURCHASING';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'sales' AND r.name = 'SALES';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'accountant' AND r.name = 'ACCOUNTANT';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'demo' AND r.name IN ('ADMIN', 'WAREHOUSE_STAFF');
