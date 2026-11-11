-- V13__create_stock_movements.sql: Sổ kho ghi mọi biến động tồn kho, nguồn số liệu cho báo cáo Xuất - Nhập - Tồn,
-- thẻ kho chi tiết và truy vết theo số lô (WMS-17.1, WMS-17.2).
--
-- Trước migration này hệ thống chỉ lưu tồn hiện tại (stock_items) mà không lưu lịch sử biến động, nên không tính
-- được tồn đầu kỳ, nhập, xuất của một kỳ trong quá khứ. Từ đây mọi đường thay đổi stock_items.quantity (nhập kho
-- GRN, xuất bán, xuất điều chuyển, nhận điều chuyển, sau này là điều chỉnh kiểm kê) ghi thêm đúng một dòng
-- stock_movements trong cùng transaction.
--
-- Lịch sử trước khi có sổ kho: không dựng lại, không bịa. Migration ghi cho mỗi dòng tồn đang có một dòng số dư
-- mở sổ (OPENING_BALANCE) bằng đúng số lượng hiện tại, đặt tại thời điểm chạy migration, và ghi thời điểm đó vào
-- stock_ledger_starts cho mỗi doanh nghiệp đang có kho. Báo cáo của kỳ bắt đầu trước thời điểm này trả null cho
-- tồn đầu kỳ, nhập, xuất kèm lời giải thích; tồn cuối kỳ vẫn đúng vì đã gồm số dư mở sổ.

CREATE TABLE stock_movements (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    company_id               BIGINT         NOT NULL,
    occurred_at              DATETIME(6)    NOT NULL,
    movement_type            VARCHAR(30)    NOT NULL,
    warehouse_id             BIGINT         NOT NULL,
    bin_location_id          BIGINT         NULL,
    bin_location_code        VARCHAR(80)    NULL,
    stock_item_id            BIGINT         NULL,
    product_id               BIGINT         NOT NULL,
    sku_code                 VARCHAR(64)    NULL,
    product_name             VARCHAR(255)   NULL,
    uom                      VARCHAR(20)    NULL,
    lot_no                   VARCHAR(50)    NULL,
    mfg_date                 DATE           NULL,
    exp_date                 DATE           NULL,
    quantity_in              DECIMAL(18, 3) NOT NULL DEFAULT 0,
    quantity_out             DECIMAL(18, 3) NOT NULL DEFAULT 0,
    document_type            VARCHAR(30)    NOT NULL,
    document_id              BIGINT         NULL,
    document_number          VARCHAR(64)    NULL,
    reference_type           VARCHAR(30)    NULL,
    reference_id             BIGINT         NULL,
    reference_number         VARCHAR(64)    NULL,
    partner_id               BIGINT         NULL,
    counterpart_warehouse_id BIGINT         NULL,
    reason                   VARCHAR(255)   NULL,
    performed_by             VARCHAR(50)    NULL,
    created_at               DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at               DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by               VARCHAR(50)    NULL,
    updated_by               VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    -- Báo cáo Xuất - Nhập - Tồn và thẻ kho: lọc theo doanh nghiệp, kho, sản phẩm, khoảng thời gian
    INDEX idx_stock_movements_company_wh_product_time (company_id, warehouse_id, product_id, occurred_at),
    -- Báo cáo toàn doanh nghiệp (không lọc kho) theo khoảng thời gian
    INDEX idx_stock_movements_company_time (company_id, occurred_at),
    -- Truy vết theo số lô
    INDEX idx_stock_movements_company_lot (company_id, lot_no, product_id),
    -- Tìm các dòng của một chứng từ (ví dụ các dòng nhận của một phiếu điều chuyển)
    INDEX idx_stock_movements_company_document (company_id, document_type, document_number)
) ENGINE = InnoDB;

CREATE TABLE stock_ledger_starts (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    company_id  BIGINT      NOT NULL,
    started_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_ledger_starts_company UNIQUE (company_id)
) ENGINE = InnoDB;

-- Một thời điểm chung cho toàn bộ số dư mở sổ và mốc bắt đầu sổ kho
SET @ledger_start = UTC_TIMESTAMP(6);

-- Mốc bắt đầu sổ kho cho mọi doanh nghiệp đang có kho (kể cả kho chưa có tồn: lịch sử trước đó vẫn không có)
INSERT INTO stock_ledger_starts (company_id, started_at, created_at, updated_at, created_by, updated_by)
SELECT w.company_id, @ledger_start, @ledger_start, @ledger_start, 'system', 'system'
FROM warehouses w
GROUP BY w.company_id;

-- Số dư mở sổ: mỗi dòng tồn khác 0 một dòng, đúng số lượng, số lô, hạn dùng, vị trí hiện tại
INSERT INTO stock_movements (company_id, occurred_at, movement_type, warehouse_id, bin_location_id, bin_location_code,
                             stock_item_id, product_id, lot_no, mfg_date, exp_date, quantity_in, quantity_out,
                             document_type, reason, created_at, updated_at, created_by, updated_by)
SELECT s.company_id,
       @ledger_start,
       'OPENING_BALANCE',
       b.warehouse_id,
       b.id,
       b.code,
       s.id,
       s.product_id,
       s.lot_no,
       s.mfg_date,
       s.exp_date,
       CASE WHEN s.quantity > 0 THEN s.quantity ELSE 0 END,
       CASE WHEN s.quantity < 0 THEN -s.quantity ELSE 0 END,
       'OPENING_BALANCE',
       'Tồn kho có sẵn tại thời điểm bắt đầu ghi sổ kho, không có chứng từ gốc',
       @ledger_start,
       @ledger_start,
       'system',
       'system'
FROM stock_items s
    JOIN bin_locations b ON b.id = s.bin_location_id
WHERE s.quantity <> 0;
