-- WMS-19.3: chỉ mục theo doanh nghiệp cho các API danh sách và số liệu KPI của order-service.
-- Mọi truy vấn JPQL đều có thêm điều kiện company_id (TenantEntity), nên chỉ mục hữu ích phải bắt đầu bằng company_id.
-- InnoDB tự nối khóa chính vào cuối mỗi chỉ mục phụ: (company_id, id) cho danh sách mặc định sắp id giảm dần đọc đúng
-- 20 dòng của trang; (company_id, status) phục vụ lọc trạng thái (vẫn theo id trong cùng trạng thái) và các câu đếm
-- KPI theo trạng thái mà không phải đọc dòng.
-- Chỉ thêm và bớt chỉ mục, không đổi dữ liệu, không đụng ràng buộc duy nhất; chạy được trên dữ liệu đang có.
-- Đo trước và sau: docs/PERFORMANCE.md mục 2.

-- Đơn bán: danh sách, lọc trạng thái, KPI doanh thu theo khoảng ngày và đơn đang xử lý.
CREATE INDEX idx_sales_orders_company_id ON sales_orders (company_id, id);
CREATE INDEX idx_sales_orders_company_status_date ON sales_orders (company_id, status, order_date);
DROP INDEX idx_sales_orders_status ON sales_orders;

-- Đơn mua
CREATE INDEX idx_purchase_orders_company_id ON purchase_orders (company_id, id);
CREATE INDEX idx_purchase_orders_company_status ON purchase_orders (company_id, status);
DROP INDEX idx_purchase_orders_status ON purchase_orders;

-- Phiếu nhập kho (GRN)
CREATE INDEX idx_grn_company_id ON goods_receipt_notes (company_id, id);
CREATE INDEX idx_grn_company_status ON goods_receipt_notes (company_id, status);
DROP INDEX idx_grn_status ON goods_receipt_notes;

-- Phiếu xuất kho (GDN)
CREATE INDEX idx_gdn_company_id ON goods_delivery_notes (company_id, id);
CREATE INDEX idx_gdn_company_status ON goods_delivery_notes (company_id, status);
DROP INDEX idx_gdn_status ON goods_delivery_notes;

-- Hóa đơn mua hàng
CREATE INDEX idx_purchase_bills_company_id ON purchase_bills (company_id, id);
CREATE INDEX idx_purchase_bills_company_status ON purchase_bills (company_id, status);
DROP INDEX idx_purchase_bills_status ON purchase_bills;

-- Nhà cung cấp, khách hàng: lọc đang hoạt động, đếm và cộng công nợ của đối tác đang hoạt động (KPI).
CREATE INDEX idx_suppliers_company_active ON suppliers (company_id, active);
DROP INDEX idx_suppliers_active ON suppliers;
CREATE INDEX idx_customers_company_active ON customers (company_id, active);
DROP INDEX idx_customers_active ON customers;
