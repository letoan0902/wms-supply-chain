-- V3__create_user_warehouses.sql
-- Bảng ánh xạ người dùng và kho được phân quyền truy cập (user_warehouses).
-- Mỗi người dùng có thể được gán cho 1 hoặc nhiều kho.

CREATE TABLE user_warehouses (
    user_id      BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, warehouse_id),
    CONSTRAINT fk_user_warehouses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Seed mẫu phân quyền kho cho các tài khoản mặc định:
-- manager: được gán kho 1 và kho 2
-- staff: được gán kho 1
INSERT IGNORE INTO user_warehouses (user_id, warehouse_id)
SELECT u.id, 1 FROM users u WHERE u.username IN ('manager', 'staff');

INSERT IGNORE INTO user_warehouses (user_id, warehouse_id)
SELECT u.id, 2 FROM users u WHERE u.username = 'manager';
