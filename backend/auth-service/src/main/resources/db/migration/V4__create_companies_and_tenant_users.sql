-- V4__create_companies_and_tenant_users.sql (WMS-22.2)
-- Thêm khái niệm doanh nghiệp: bảng companies, mỗi tài khoản thuộc đúng một doanh nghiệp (users.company_id).
-- Toàn bộ tài khoản đang có được gán cho Công ty mẫu A (id = 1). Công ty mẫu B (id = 2) có một tài khoản
-- admin_b để kiểm tra cách ly. Không xóa dữ liệu nào đang có.

-- 1. Bảng doanh nghiệp. status: PENDING (chưa kích hoạt), ACTIVE, SUSPENDED (tạm khóa).
CREATE TABLE companies (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    tax_code     VARCHAR(30)  NULL,
    email        VARCHAR(100) NULL,
    phone        VARCHAR(30)  NULL,
    address      VARCHAR(255) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    activated_at DATETIME(6)  NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_companies_code UNIQUE (code)
) ENGINE = InnoDB;

-- 2. Hai doanh nghiệp mẫu, mã dạng CTY cộng 6 chữ số theo id.
INSERT INTO companies (id, code, name, status, activated_at, created_by, updated_by) VALUES
    (1, 'CTY000001', 'Công ty mẫu A', 'ACTIVE', UTC_TIMESTAMP(6), 'system', 'system'),
    (2, 'CTY000002', 'Công ty mẫu B', 'ACTIVE', UTC_TIMESTAMP(6), 'system', 'system');

-- 3. Thêm cột company_id (cho phép NULL tạm thời), gán toàn bộ tài khoản đang có vào doanh nghiệp 1,
--    rồi mới đặt NOT NULL, thêm index và khóa ngoại.
ALTER TABLE users ADD COLUMN company_id BIGINT NULL AFTER id;

UPDATE users SET company_id = 1 WHERE company_id IS NULL;

ALTER TABLE users MODIFY company_id BIGINT NOT NULL;

CREATE INDEX idx_users_company_id ON users (company_id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id);

-- 4. Tài khoản admin_b (vai trò ADMIN) thuộc Công ty mẫu B.
--    Mật khẩu theo quy ước seed hiện có: dùng lại mật khẩu đã băm của tài khoản admin, không ghi mật khẩu
--    hay chuỗi băm vào file này. Không có tài khoản admin thì câu lệnh không thêm dòng nào;
--    INSERT IGNORE bỏ qua nếu tên đăng nhập hoặc email admin_b đã tồn tại.
INSERT IGNORE INTO users (username, email, password_hash, full_name, enabled, company_id, created_by, updated_by)
SELECT 'admin_b', 'admin_b@wms.local', u.password_hash, 'Quản trị viên Công ty mẫu B', TRUE, 2, 'system', 'system'
FROM users u
WHERE u.username = 'admin';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin_b' AND r.name = 'ADMIN';
