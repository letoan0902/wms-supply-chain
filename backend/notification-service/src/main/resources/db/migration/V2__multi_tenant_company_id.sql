-- V2__multi_tenant_company_id.sql: tách nhật ký thông báo theo doanh nghiệp (WMS-22.4c).
-- notification_logs là bảng duy nhất của notification-service. Dữ liệu đang có thuộc doanh nghiệp mẫu (id = 1),
-- từ đây cột company_id bắt buộc và do Hibernate (@TenantId của TenantEntity) tự gán, tự lọc.
-- Không tạo khóa ngoại tới bảng companies vì bảng đó nằm ở cơ sở dữ liệu của auth-service.

-- 1. Thêm cột cho phép null để dữ liệu cũ không lỗi
ALTER TABLE notification_logs ADD COLUMN company_id BIGINT NULL;

-- 2. Dữ liệu đang có thuộc doanh nghiệp mẫu (id = 1)
UPDATE notification_logs SET company_id = 1 WHERE company_id IS NULL;

-- 3. Bắt buộc từ đây về sau
ALTER TABLE notification_logs MODIFY company_id BIGINT NOT NULL;

-- 4. Index bắt đầu bằng company_id cho các cột hay lọc (trạng thái, thời gian tạo)
CREATE INDEX idx_notification_logs_company_status ON notification_logs (company_id, status);
CREATE INDEX idx_notification_logs_company_created ON notification_logs (company_id, created_at);
