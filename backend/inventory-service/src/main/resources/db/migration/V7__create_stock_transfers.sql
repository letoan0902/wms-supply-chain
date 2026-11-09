-- V7__create_stock_transfers.sql: Quản lý điều chuyển hàng hóa nội bộ giữa các kho (WMS-13.1 - FR08)
-- Hỗ trợ theo dõi luồng điều chuyển từ DRAFT -> IN_TRANSIT -> COMPLETED / PARTIAL_RECEIVED.
-- Bảng kế thừa thiết kế đa doanh nghiệp (company_id) ngay từ đầu.

CREATE TABLE stock_transfers (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    company_id               BIGINT         NOT NULL,
    transfer_number          VARCHAR(32)    NOT NULL,
    source_warehouse_id      BIGINT         NOT NULL,
    destination_warehouse_id BIGINT         NOT NULL,
    status                   VARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
    transfer_date            DATETIME(6)    NULL,
    expected_delivery_date   DATETIME(6)    NULL,
    notes                    VARCHAR(500)   NULL,
    approved_by              VARCHAR(50)    NULL,
    approved_at              DATETIME(6)    NULL,
    received_by              VARCHAR(50)    NULL,
    received_at              DATETIME(6)    NULL,
    created_at               DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at               DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by               VARCHAR(50)    NULL,
    updated_by               VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_transfers_company_number UNIQUE (company_id, transfer_number),
    CONSTRAINT fk_stock_transfers_source_wh FOREIGN KEY (source_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_stock_transfers_dest_wh FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses (id),
    INDEX idx_stock_transfers_company (company_id),
    INDEX idx_stock_transfers_source_wh (source_warehouse_id),
    INDEX idx_stock_transfers_dest_wh (destination_warehouse_id),
    INDEX idx_stock_transfers_status (status)
) ENGINE = InnoDB;

CREATE TABLE stock_transfer_items (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    company_id                  BIGINT         NOT NULL,
    transfer_id                 BIGINT         NOT NULL,
    product_id                  BIGINT         NOT NULL,
    sku_code                    VARCHAR(64)    NOT NULL,
    product_name                VARCHAR(255)   NULL,
    uom                         VARCHAR(20)    NULL,
    source_bin_location_id      BIGINT         NULL,
    destination_bin_location_id BIGINT         NULL,
    quantity                    DECIMAL(18, 3) NOT NULL,
    transferred_quantity        DECIMAL(18, 3) NOT NULL DEFAULT 0,
    received_quantity           DECIMAL(18, 3) NOT NULL DEFAULT 0,
    damaged_quantity            DECIMAL(18, 3) NOT NULL DEFAULT 0,
    notes                       VARCHAR(255)   NULL,
    created_at                  DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at                  DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by                  VARCHAR(50)    NULL,
    updated_by                  VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transfer_items_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_items_source_bin FOREIGN KEY (source_bin_location_id) REFERENCES bin_locations (id) ON DELETE SET NULL,
    CONSTRAINT fk_transfer_items_dest_bin FOREIGN KEY (destination_bin_location_id) REFERENCES bin_locations (id) ON DELETE SET NULL,
    INDEX idx_transfer_items_company (company_id),
    INDEX idx_transfer_items_transfer (transfer_id),
    INDEX idx_transfer_items_product (product_id)
) ENGINE = InnoDB;

