-- ============================================================================
-- Migration V4: Bảng companies, liên kết users.company_id và seed 2 công ty mẫu (WMS-22.2)
-- ============================================================================

-- 1. Bảng companies: Quản lý các doanh nghiệp sử dụng nền tảng SaaS
CREATE TABLE companies (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    tax_code   VARCHAR(30)  NULL,
    email      VARCHAR(100) NULL,
    phone      VARCHAR(30)  NULL,
    address    VARCHAR(255) NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6)  NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by VARCHAR(50)  NULL,
    updated_by VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_companies_code UNIQUE (code),
    CONSTRAINT uk_companies_tax_code UNIQUE (tax_code)
) ENGINE = InnoDB;

-- 2. Bổ sung trường company_id vào bảng users
ALTER TABLE users
    ADD COLUMN company_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id),
    ADD INDEX idx_users_company_id (company_id);

-- 3. Dọn dẹp tài khoản demo dùng chung
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = 'demo');
DELETE FROM user_warehouses WHERE user_id IN (SELECT id FROM users WHERE username = 'demo');
DELETE FROM users WHERE username = 'demo';

-- 4. Seed dữ liệu 2 công ty mẫu phục vụ demo đa doanh nghiệp
INSERT INTO companies (id, code, name, tax_code, email, phone, address, status, created_at, updated_at, created_by, updated_by)
VALUES
    (1, 'LOGIX',    'Công ty Cổ phần Vận tải & Kho vận LogiX', '0101234567', 'contact@logix.vn',    '02438889999', 'Số 15 Lê Duẩn, P. Điện Biên, Q. Ba Đình, Hà Nội', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 'FASTMOVE', 'Công ty TNHH Phân phối Nhanh FastMove',  '0309876543', 'info@fastmove.vn',   '02837778888', 'Tòa nhà Landmark 81, P. 22, Q. Bình Thạnh, TP. HCM', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 5. Seed tài khoản cho Công ty 1 (LogiX) - Mật khẩu: Demopassword
INSERT INTO users (username, email, password_hash, full_name, enabled, company_id, created_by, updated_by)
VALUES
    ('admin_logix',   'admin@logix.vn',   '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Quản trị viên LogiX',   TRUE, 1, 'system', 'system'),
    ('manager_logix', 'manager@logix.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Quản lý kho LogiX',     TRUE, 1, 'system', 'system'),
    ('staff_logix',   'staff@logix.vn',   '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Nhân viên kho LogiX',    TRUE, 1, 'system', 'system')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

-- Gán vai trò cho user Công ty 1
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin_logix' AND r.name = 'ADMIN';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'manager_logix' AND r.name = 'WAREHOUSE_MANAGER';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'staff_logix' AND r.name = 'WAREHOUSE_STAFF';

-- Phân quyền kho cho user Công ty 1 (kho 1 và kho 2)
INSERT IGNORE INTO user_warehouses (user_id, warehouse_id)
SELECT u.id, 1 FROM users u WHERE u.username IN ('manager_logix', 'staff_logix');

INSERT IGNORE INTO user_warehouses (user_id, warehouse_id)
SELECT u.id, 2 FROM users u WHERE u.username = 'manager_logix';

-- 6. Seed tài khoản cho Công ty 2 (FastMove) - Mật khẩu: Demopassword
INSERT INTO users (username, email, password_hash, full_name, enabled, company_id, created_by, updated_by)
VALUES
    ('admin_fastmove', 'admin@fastmove.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Quản trị viên FastMove', TRUE, 2, 'system', 'system'),
    ('staff_fastmove', 'staff@fastmove.vn', '$2a$10$7rENUfc.LEGiRRDKjtL9aOJ56rQnYAhAE1aTie4BKyMRvZ0dBwMX2', 'Nhân viên kho FastMove',  TRUE, 2, 'system', 'system')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

-- Gán vai trò cho user Công ty 2
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin_fastmove' AND r.name = 'ADMIN';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'staff_fastmove' AND r.name = 'WAREHOUSE_STAFF';

-- Phân quyền kho cho user Công ty 2 (kho 3)
INSERT IGNORE INTO user_warehouses (user_id, warehouse_id)
SELECT u.id, 3 FROM users u WHERE u.username = 'staff_fastmove';
