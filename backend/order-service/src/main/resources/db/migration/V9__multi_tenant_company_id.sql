-- V9__multi_tenant_company_id.sql: Tách dữ liệu order-service theo doanh nghiệp (WMS-22.4b, docs/MULTI_TENANT.md).
-- Mỗi bảng nghiệp vụ có cột company_id: dữ liệu đang có thuộc doanh nghiệp 1, bảng con lấy company_id từ bảng cha.
-- Mã đối tác và số chứng từ đổi từ duy nhất toàn hệ thống sang duy nhất trong một doanh nghiệp (company_id, mã).
-- Ràng buộc duy nhất bắt đầu bằng company_id đồng thời là index cho company_id nên không tạo thêm index trùng.
-- Các ràng buộc duy nhất bị thay đều nằm trên cột mã/số chứng từ, không khóa ngoại nào dựa vào chúng
-- (khóa ngoại của order-service đều trỏ tới cột id và có index riêng ở bảng con), nên xóa được an toàn.
-- Không tạo khóa ngoại tới bảng companies vì bảng đó nằm ở cơ sở dữ liệu của auth-service.

-- 1. Nhà cung cấp (suppliers)
ALTER TABLE suppliers ADD COLUMN company_id BIGINT NULL;
UPDATE suppliers SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE suppliers MODIFY company_id BIGINT NOT NULL;

ALTER TABLE suppliers DROP INDEX uk_suppliers_code;
ALTER TABLE suppliers ADD CONSTRAINT uk_suppliers_company_code UNIQUE (company_id, code);
-- Kiểm tra trùng mã số thuế giờ xét trong một doanh nghiệp
ALTER TABLE suppliers DROP INDEX idx_suppliers_tax_code;
CREATE INDEX idx_suppliers_company_tax_code ON suppliers (company_id, tax_code);

-- 2. Khách hàng (customers)
ALTER TABLE customers ADD COLUMN company_id BIGINT NULL;
UPDATE customers SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE customers MODIFY company_id BIGINT NOT NULL;

ALTER TABLE customers DROP INDEX uk_customers_code;
ALTER TABLE customers ADD CONSTRAINT uk_customers_company_code UNIQUE (company_id, code);
ALTER TABLE customers DROP INDEX idx_customers_tax_code;
CREATE INDEX idx_customers_company_tax_code ON customers (company_id, tax_code);

-- 3. Giao dịch công nợ nhà cung cấp (supplier_transactions)
ALTER TABLE supplier_transactions ADD COLUMN company_id BIGINT NULL;
UPDATE supplier_transactions t JOIN suppliers s ON t.supplier_id = s.id
    SET t.company_id = s.company_id WHERE t.company_id IS NULL;
UPDATE supplier_transactions SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE supplier_transactions MODIFY company_id BIGINT NOT NULL;
CREATE INDEX idx_supplier_transactions_company ON supplier_transactions (company_id);

-- 4. Giao dịch công nợ khách hàng (customer_transactions)
ALTER TABLE customer_transactions ADD COLUMN company_id BIGINT NULL;
UPDATE customer_transactions t JOIN customers c ON t.customer_id = c.id
    SET t.company_id = c.company_id WHERE t.company_id IS NULL;
UPDATE customer_transactions SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE customer_transactions MODIFY company_id BIGINT NOT NULL;
CREATE INDEX idx_customer_transactions_company ON customer_transactions (company_id);

-- 5. Đơn mua hàng (purchase_orders)
ALTER TABLE purchase_orders ADD COLUMN company_id BIGINT NULL;
UPDATE purchase_orders po JOIN suppliers s ON po.supplier_id = s.id
    SET po.company_id = s.company_id WHERE po.company_id IS NULL;
UPDATE purchase_orders SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE purchase_orders MODIFY company_id BIGINT NOT NULL;

ALTER TABLE purchase_orders DROP INDEX uk_purchase_orders_number;
ALTER TABLE purchase_orders ADD CONSTRAINT uk_purchase_orders_company_number UNIQUE (company_id, po_number);

-- 6. Dòng hàng đơn mua (po_items)
ALTER TABLE po_items ADD COLUMN company_id BIGINT NULL;
UPDATE po_items i JOIN purchase_orders po ON i.po_id = po.id
    SET i.company_id = po.company_id WHERE i.company_id IS NULL;
UPDATE po_items SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE po_items MODIFY company_id BIGINT NOT NULL;
CREATE INDEX idx_po_items_company ON po_items (company_id);

-- 7. Đơn bán hàng (sales_orders)
ALTER TABLE sales_orders ADD COLUMN company_id BIGINT NULL;
UPDATE sales_orders so JOIN customers c ON so.customer_id = c.id
    SET so.company_id = c.company_id WHERE so.company_id IS NULL;
UPDATE sales_orders SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE sales_orders MODIFY company_id BIGINT NOT NULL;

ALTER TABLE sales_orders DROP INDEX uk_sales_orders_number;
ALTER TABLE sales_orders ADD CONSTRAINT uk_sales_orders_company_number UNIQUE (company_id, so_number);

-- 8. Dòng hàng đơn bán (so_items)
ALTER TABLE so_items ADD COLUMN company_id BIGINT NULL;
UPDATE so_items i JOIN sales_orders so ON i.so_id = so.id
    SET i.company_id = so.company_id WHERE i.company_id IS NULL;
UPDATE so_items SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE so_items MODIFY company_id BIGINT NOT NULL;
CREATE INDEX idx_so_items_company ON so_items (company_id);

-- 9. Phiếu nhập kho (goods_receipt_notes)
ALTER TABLE goods_receipt_notes ADD COLUMN company_id BIGINT NULL;
UPDATE goods_receipt_notes g JOIN purchase_orders po ON g.po_id = po.id
    SET g.company_id = po.company_id WHERE g.company_id IS NULL;
UPDATE goods_receipt_notes SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE goods_receipt_notes MODIFY company_id BIGINT NOT NULL;

ALTER TABLE goods_receipt_notes DROP INDEX uk_goods_receipt_notes_number;
ALTER TABLE goods_receipt_notes ADD CONSTRAINT uk_goods_receipt_notes_company_number UNIQUE (company_id, grn_number);

-- 10. Dòng hàng phiếu nhập kho (goods_receipt_note_items)
ALTER TABLE goods_receipt_note_items ADD COLUMN company_id BIGINT NULL;
UPDATE goods_receipt_note_items i JOIN goods_receipt_notes g ON i.grn_id = g.id
    SET i.company_id = g.company_id WHERE i.company_id IS NULL;
UPDATE goods_receipt_note_items SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE goods_receipt_note_items MODIFY company_id BIGINT NOT NULL;
CREATE INDEX idx_grn_items_company ON goods_receipt_note_items (company_id);

-- 11. Hóa đơn mua hàng (purchase_bills). Cột bill_number được khai báo UNIQUE ngay trên cột ở V7 nên MySQL đặt tên
-- ràng buộc là bill_number. Ràng buộc uk_purchase_bills_grn (mỗi phiếu nhập một hóa đơn) giữ nguyên vì grn_id là
-- khóa chính toàn hệ thống và khóa ngoại fk_purchase_bills_grn dùng index của nó.
ALTER TABLE purchase_bills ADD COLUMN company_id BIGINT NULL;
UPDATE purchase_bills b JOIN goods_receipt_notes g ON b.grn_id = g.id
    SET b.company_id = g.company_id WHERE b.company_id IS NULL;
UPDATE purchase_bills SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE purchase_bills MODIFY company_id BIGINT NOT NULL;

ALTER TABLE purchase_bills DROP INDEX bill_number;
ALTER TABLE purchase_bills ADD CONSTRAINT uk_purchase_bills_company_number UNIQUE (company_id, bill_number);

-- 12. Dòng hàng hóa đơn mua (purchase_bill_items)
ALTER TABLE purchase_bill_items ADD COLUMN company_id BIGINT NULL;
UPDATE purchase_bill_items i JOIN purchase_bills b ON i.bill_id = b.id
    SET i.company_id = b.company_id WHERE i.company_id IS NULL;
UPDATE purchase_bill_items SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE purchase_bill_items MODIFY company_id BIGINT NOT NULL;
CREATE INDEX idx_pb_items_company ON purchase_bill_items (company_id);

-- 13. Phiếu xuất kho (goods_delivery_notes)
ALTER TABLE goods_delivery_notes ADD COLUMN company_id BIGINT NULL;
UPDATE goods_delivery_notes g JOIN sales_orders so ON g.so_id = so.id
    SET g.company_id = so.company_id WHERE g.company_id IS NULL;
UPDATE goods_delivery_notes SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE goods_delivery_notes MODIFY company_id BIGINT NOT NULL;

ALTER TABLE goods_delivery_notes DROP INDEX uk_goods_delivery_notes_number;
ALTER TABLE goods_delivery_notes ADD CONSTRAINT uk_goods_delivery_notes_company_number UNIQUE (company_id, gdn_number);

-- 14. Dòng hàng phiếu xuất kho (goods_delivery_note_items)
ALTER TABLE goods_delivery_note_items ADD COLUMN company_id BIGINT NULL;
UPDATE goods_delivery_note_items i JOIN goods_delivery_notes g ON i.gdn_id = g.id
    SET i.company_id = g.company_id WHERE i.company_id IS NULL;
UPDATE goods_delivery_note_items SET company_id = 1 WHERE company_id IS NULL;
ALTER TABLE goods_delivery_note_items MODIFY company_id BIGINT NOT NULL;
CREATE INDEX idx_gdn_items_company ON goods_delivery_note_items (company_id);

-- 15. Bộ đếm số chứng từ theo doanh nghiệp (document_sequences). Mỗi dòng là một tiền tố, ví dụ PO-20261110-,
-- last_number là số thứ tự đã cấp gần nhất. Số mới được cấp trong một transaction ngắn có khóa dòng
-- (SELECT ... FOR UPDATE) nên hai yêu cầu đồng thời, kể cả ở hai phiên bản service, không nhận trùng số.
CREATE TABLE document_sequences (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    company_id  BIGINT      NOT NULL,
    seq_prefix  VARCHAR(30) NOT NULL,
    last_number BIGINT      NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_sequences_company_prefix UNIQUE (company_id, seq_prefix)
) ENGINE = InnoDB;
