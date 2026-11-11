-- ============================================================================
-- Migration V4: Tách danh mục hàng hóa theo doanh nghiệp (multi-tenant) cho product-service (WMS-22.4b)
-- Thêm cột company_id cho mọi bảng của service, gán dữ liệu đang có cho Công ty mẫu A (company_id = 1),
-- đổi ràng buộc duy nhất thành duy nhất trong một doanh nghiệp: (company_id, code) cho danh mục và đơn vị tính,
-- (company_id, sku) và (company_id, barcode) cho hàng hóa.
-- Các đơn vị tính mẫu do V3 thêm cũng thuộc doanh nghiệp 1; doanh nghiệp mới đăng ký bắt đầu với danh mục rỗng.
-- Không tạo khóa ngoại tới bảng companies vì bảng đó nằm ở cơ sở dữ liệu của auth-service.
-- ============================================================================

-- 1. Danh mục (categories)
ALTER TABLE categories ADD COLUMN company_id BIGINT NULL;
UPDATE categories SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE categories MODIFY company_id BIGINT NOT NULL;

ALTER TABLE categories DROP INDEX uk_categories_code;
ALTER TABLE categories ADD CONSTRAINT uk_categories_company_code UNIQUE (company_id, code);

-- 2. Đơn vị tính (uoms)
ALTER TABLE uoms ADD COLUMN company_id BIGINT NULL;
UPDATE uoms SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE uoms MODIFY company_id BIGINT NOT NULL;

ALTER TABLE uoms DROP INDEX uk_uoms_code;
ALTER TABLE uoms ADD CONSTRAINT uk_uoms_company_code UNIQUE (company_id, code);

-- 3. Hàng hóa (products)
-- Ràng buộc (company_id, barcode): MySQL cho phép nhiều dòng có barcode NULL trong một chỉ mục duy nhất, nên
-- hàng hóa chưa có mã vạch vẫn tạo được không giới hạn như trước (service đổi barcode rỗng thành NULL).
ALTER TABLE products ADD COLUMN company_id BIGINT NULL;
UPDATE products SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE products MODIFY company_id BIGINT NOT NULL;

ALTER TABLE products DROP INDEX uk_products_sku;
ALTER TABLE products DROP INDEX uk_products_barcode;
ALTER TABLE products ADD CONSTRAINT uk_products_company_sku UNIQUE (company_id, sku);
ALTER TABLE products ADD CONSTRAINT uk_products_company_barcode UNIQUE (company_id, barcode);
-- Đếm SKU đang hoạt động của doanh nghiệp (giới hạn gói) và lọc danh sách theo trạng thái
CREATE INDEX idx_products_company_active ON products (company_id, active);

-- 4. Quy đổi đơn vị tính (unit_conversions): lấy doanh nghiệp từ sản phẩm cha
ALTER TABLE unit_conversions ADD COLUMN company_id BIGINT NULL;
UPDATE unit_conversions uc JOIN products p ON uc.product_id = p.id SET uc.company_id = p.company_id
    WHERE uc.company_id IS NULL;
UPDATE unit_conversions SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE unit_conversions MODIFY company_id BIGINT NOT NULL;

CREATE INDEX idx_unit_conversions_company ON unit_conversions (company_id);
