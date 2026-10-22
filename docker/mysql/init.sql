-- ============================================================================
-- Khởi tạo CSDL cho WMS Supply Chain.
-- Mỗi microservice sở hữu một schema riêng (database-per-service).
-- Script chỉ chạy tự động một lần, khi volume mysql_data còn trống
-- (cơ chế /docker-entrypoint-initdb.d của image mysql).
-- ============================================================================

CREATE DATABASE IF NOT EXISTS auth_db         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS product_db      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS inventory_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_db        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tài khoản ứng dụng `wms`.
-- Khi chạy bằng docker compose, image mysql đã tạo sẵn user này từ biến DB_USERNAME/DB_PASSWORD
-- (trong file .env) trước khi script chạy, nên lệnh CREATE USER bên dưới không đổi mật khẩu.
-- Mật khẩu mặc định chỉ có tác dụng khi chạy script thủ công trên một MySQL khác (môi trường dev).
CREATE USER IF NOT EXISTS 'wms'@'%' IDENTIFIED BY 'wms_dev_password';

GRANT ALL PRIVILEGES ON auth_db.*         TO 'wms'@'%';
GRANT ALL PRIVILEGES ON product_db.*      TO 'wms'@'%';
GRANT ALL PRIVILEGES ON inventory_db.*    TO 'wms'@'%';
GRANT ALL PRIVILEGES ON order_db.*        TO 'wms'@'%';
GRANT ALL PRIVILEGES ON notification_db.* TO 'wms'@'%';

FLUSH PRIVILEGES;
