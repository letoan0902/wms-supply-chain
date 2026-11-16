-- ============================================================================
-- Migration V9: Quản trị nền tảng (vai trò SUPER_ADMIN, doanh nghiệp hệ thống PLATFORM), docs/SAAS_SPEC.md mục 6
-- ============================================================================
-- Tài khoản admin đang có trở thành quản trị nền tảng: chuyển sang doanh nghiệp PLATFORM, bỏ mọi vai trò cũ và phân
-- công kho, gán SUPER_ADMIN. Công ty mẫu A (id = 1) nhận tài khoản admin_a (vai trò ADMIN) thay cho admin, tạo theo
-- đúng cách V4 tạo admin_b: dùng lại mật khẩu đã băm của admin, không ghi mật khẩu hay chuỗi băm vào file này.
-- Không có tài khoản admin thì chỉ thêm dòng vai trò và doanh nghiệp PLATFORM (chưa có ai), không đổi gì khác.
-- Không đổi cấu trúc bảng.

-- 1. Vai trò quản trị nền tảng. Không nằm trong Roles.ALL nên admin của doanh nghiệp không gán được.
INSERT IGNORE INTO roles (name, description, created_by, updated_by)
VALUES ('SUPER_ADMIN', 'Platform administrator', 'system', 'system');

-- 2. Doanh nghiệp hệ thống chứa tài khoản quản trị nền tảng: ACTIVE, không có thuê bao, không bao giờ xuất hiện trong
--    danh sách, thống kê hay tác vụ định kỳ dành cho khách hàng. Mã doanh nghiệp khách hàng luôn dạng CTY cộng 6 chữ
--    số nên không trùng mã PLATFORM; INSERT IGNORE bỏ qua khi dòng đã có.
INSERT IGNORE INTO companies (code, name, status, activated_at, created_by, updated_by)
VALUES ('PLATFORM', 'Quản trị nền tảng', 'ACTIVE', UTC_TIMESTAMP(6), 'system', 'system');

-- 3. Tài khoản admin_a (vai trò ADMIN) của Công ty mẫu A, chỉ tạo khi admin còn thuộc doanh nghiệp 1. Không có tài
--    khoản admin thì câu lệnh không thêm dòng nào; INSERT IGNORE bỏ qua nếu tên đăng nhập hoặc email admin_a đã có.
INSERT IGNORE INTO users (username, email, password_hash, full_name, enabled, company_id, created_by, updated_by)
SELECT 'admin_a', 'admin_a@wms.local', u.password_hash, 'Quản trị viên Công ty mẫu A', TRUE, 1, 'system', 'system'
FROM users u
WHERE u.username = 'admin' AND u.company_id = 1;

-- Chỉ gán cho đúng tài khoản admin_a do migration tạo ở doanh nghiệp 1 (created_by = 'system'), không bao giờ cho một
-- tài khoản admin_a do người dùng tạo trước đó.
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT a.id, r.id
FROM users a
JOIN roles r ON r.name = 'ADMIN'
WHERE a.username = 'admin_a' AND a.company_id = 1 AND a.created_by = 'system';

-- admin_a nhận lại phân công kho của admin (nếu có) để Công ty mẫu A dùng như trước.
INSERT IGNORE INTO user_warehouses (user_id, warehouse_id)
SELECT a.id, uw.warehouse_id
FROM users a
JOIN users u ON u.username = 'admin' AND u.company_id = 1
JOIN user_warehouses uw ON uw.user_id = u.id
WHERE a.username = 'admin_a' AND a.company_id = 1 AND a.created_by = 'system';

-- 4. Chuyển admin sang doanh nghiệp PLATFORM. Không có admin hoặc không có PLATFORM thì không dòng nào đổi.
UPDATE users u
JOIN companies p ON p.code = 'PLATFORM'
SET u.company_id = p.id
WHERE u.username = 'admin';

-- 5. Bỏ mọi vai trò và phân công kho cũ của admin, chỉ khi admin đã nằm trong PLATFORM.
DELETE ur
FROM user_roles ur
JOIN users u ON u.id = ur.user_id
JOIN companies c ON c.id = u.company_id
WHERE u.username = 'admin' AND c.code = 'PLATFORM';

DELETE uw
FROM user_warehouses uw
JOIN users u ON u.id = uw.user_id
JOIN companies c ON c.id = u.company_id
WHERE u.username = 'admin' AND c.code = 'PLATFORM';

-- 6. Gán SUPER_ADMIN cho admin (đang thuộc PLATFORM).
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN companies c ON c.id = u.company_id AND c.code = 'PLATFORM'
JOIN roles r ON r.name = 'SUPER_ADMIN'
WHERE u.username = 'admin';
