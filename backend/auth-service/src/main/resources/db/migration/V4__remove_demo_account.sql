-- V4__remove_demo_account.sql
-- WMS-23.0: bỏ tài khoản demo (có quyền ADMIN, mật khẩu công khai trong repository) khỏi dữ liệu mẫu.
-- Sau migration này chỉ còn 6 tài khoản nghiệp vụ mẫu của V2; mật khẩu của chúng phải được đổi ngay sau lần
-- triển khai đầu bằng deploy/rotate-seed-passwords.sh (hoặc API POST /api/auth/users/change-password).
-- Xóa bảng con trước để không phụ thuộc vào ON DELETE CASCADE của từng khóa ngoại.
DELETE FROM user_warehouses
WHERE user_id IN (SELECT id FROM users WHERE username = 'demo' AND email = 'demo@example.com');

DELETE FROM user_roles
WHERE user_id IN (SELECT id FROM users WHERE username = 'demo' AND email = 'demo@example.com');

DELETE FROM users
WHERE username = 'demo' AND email = 'demo@example.com';
