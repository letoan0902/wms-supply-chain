-- WMS-10.1: Phiếu nhập kho (Goods Receipt Note - GRN), hỗ trợ nhập từng phần (Partial Receipt) và ghi nhận số lượng đạt / hỏng (FR11)

-- 1. Bổ sung cột received_quantity vào po_items để theo dõi số lượng đã nhập thực tế
ALTER TABLE po_items
    ADD COLUMN received_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000 AFTER quantity;

-- 2. Tạo bảng goods_receipt_notes lưu thông tin phiếu nhập kho
CREATE TABLE goods_receipt_notes (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    grn_number              VARCHAR(30)    NOT NULL,
    po_id                   BIGINT         NOT NULL,
    po_number               VARCHAR(30)    NOT NULL,
    supplier_id             BIGINT         NOT NULL,
    supplier_name           VARCHAR(150)   NULL,
    warehouse_id            BIGINT         NOT NULL,
    warehouse_name          VARCHAR(100)   NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'COMPLETED',
    receipt_date            DATE           NOT NULL,
    total_accepted_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    total_rejected_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    total_amount            DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    note                    VARCHAR(500)   NULL,
    received_by             VARCHAR(50)    NULL,
    created_at              DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by              VARCHAR(50)    NULL,
    updated_by              VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_goods_receipt_notes_number UNIQUE (grn_number),
    CONSTRAINT fk_goods_receipt_notes_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_goods_receipt_notes_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    INDEX idx_grn_po (po_id),
    INDEX idx_grn_status (status),
    INDEX idx_grn_warehouse (warehouse_id),
    INDEX idx_grn_supplier (supplier_id),
    INDEX idx_grn_receipt_date (receipt_date)
) ENGINE = InnoDB;

-- 3. Tạo bảng goods_receipt_note_items lưu chi tiết dòng hàng nhập kho
CREATE TABLE goods_receipt_note_items (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    grn_id            BIGINT         NOT NULL,
    po_item_id        BIGINT         NOT NULL,
    product_id        BIGINT         NULL,
    sku_code          VARCHAR(50)    NOT NULL,
    product_name      VARCHAR(255)   NOT NULL,
    uom               VARCHAR(30)    NULL,
    ordered_quantity  DECIMAL(18, 3) NOT NULL,
    accepted_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    rejected_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0.000,
    unit_price        DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    line_total        DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    rejected_reason   VARCHAR(255)   NULL,
    batch_number      VARCHAR(50)    NULL,
    mfg_date          DATE           NULL,
    exp_date          DATE           NULL,
    location_id       BIGINT         NULL,
    location_code     VARCHAR(50)    NULL,
    note              VARCHAR(255)   NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at        DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by        VARCHAR(50)    NULL,
    updated_by        VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grn_items_grn FOREIGN KEY (grn_id) REFERENCES goods_receipt_notes (id) ON DELETE CASCADE,
    CONSTRAINT fk_grn_items_po_item FOREIGN KEY (po_item_id) REFERENCES po_items (id),
    INDEX idx_grn_items_grn (grn_id),
    INDEX idx_grn_items_po_item (po_item_id),
    INDEX idx_grn_items_sku (sku_code)
) ENGINE = InnoDB;
