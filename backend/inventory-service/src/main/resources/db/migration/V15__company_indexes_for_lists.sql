-- WMS-19.3: chỉ mục theo doanh nghiệp cho các API danh sách, tra cứu và tác vụ định kỳ của inventory-service.
-- Mọi truy vấn JPQL đều có thêm điều kiện company_id (TenantEntity), nên chỉ mục hữu ích phải bắt đầu bằng company_id.
-- Chỉ thêm và bớt chỉ mục, không đổi dữ liệu, không đụng ràng buộc duy nhất; chạy được trên dữ liệu đang có.
-- Đo trước và sau: docs/PERFORMANCE.md mục 2.

-- stock_items: danh sách lô sắp theo hạn dùng (GET /api/inventory/lots), lô cận hạn, tác vụ tự khóa lô hết hạn
-- (ExpiredLotScheduler, mỗi doanh nghiệp một lần mỗi đêm).
CREATE INDEX idx_stock_items_company_exp_date ON stock_items (company_id, exp_date);
-- (exp_date) không còn truy vấn nào dùng một mình: truy vấn theo hạn dùng luôn kèm company_id.
DROP INDEX idx_stock_items_exp_date ON stock_items;
-- (company_id) là tiền tố của idx_stock_items_company_product (company_id, product_id).
DROP INDEX idx_stock_items_company ON stock_items;

-- stock_reservations: chống xử lý trùng sự kiện order.created theo messageId (existsByMessageId, mỗi lần giữ chỗ).
CREATE INDEX idx_stock_reservations_company_message ON stock_reservations (company_id, message_id);
-- (company_id) là tiền tố của chỉ mục vừa thêm.
DROP INDEX idx_stock_reservations_company ON stock_reservations;

-- stock_transfers: danh sách lệnh điều chuyển, mặc định sắp createdAt giảm dần, có thể lọc trạng thái.
CREATE INDEX idx_stock_transfers_company_created ON stock_transfers (company_id, created_at);
CREATE INDEX idx_stock_transfers_company_status_created ON stock_transfers (company_id, status, created_at);
-- (company_id) là tiền tố của idx_stock_transfers_company_created; (status) thay bằng chỉ mục có company_id ở trên.
DROP INDEX idx_stock_transfers_company ON stock_transfers;
DROP INDEX idx_stock_transfers_status ON stock_transfers;

-- inventory_lots: (company_id) là tiền tố của idx_inventory_lots_company_product (company_id, product_id).
DROP INDEX idx_inventory_lots_company ON inventory_lots;

-- bin_locations: (warehouse_id, zone, rack) là tiền tố của ràng buộc duy nhất uk_bin_locations_position
-- (warehouse_id, zone, rack, bin); khóa ngoại warehouse_id vẫn có chỉ mục của ràng buộc đó và idx_bin_locations_warehouse_status.
DROP INDEX idx_bin_locations_zone_rack ON bin_locations;
