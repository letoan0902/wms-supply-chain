-- V12__create_stock_adjustments.sql: Phiếu điều chỉnh tồn kho sinh từ chênh lệch kiểm kê (WMS-14.2)
-- Mỗi đợt kiểm kê có tối đa một phiếu điều chỉnh: ràng buộc duy nhất (company_id, session_id) chặn việc sinh
-- phiếu hai lần khi bấm duyệt hai lần hoặc duyệt đồng thời. Mã phiếu ADJ-yyyyMM-NN duy nhất theo (company_id, code).
-- quantity_before và quantity_after của dòng phiếu ghi tồn tại vị trí ngay trước và sau khi Kế toán duyệt (audit).

CREATE TABLE stock_adjustments (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    company_id              BIGINT         NOT NULL,
    code                    VARCHAR(30)    NOT NULL,
    session_id              BIGINT         NOT NULL,
    warehouse_id            BIGINT         NOT NULL,
    status                  VARCHAR(30)    NOT NULL DEFAULT 'PENDING_APPROVAL',
    total_increase_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0,
    total_decrease_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0,
    notes                   VARCHAR(500)   NULL,
    requested_by            VARCHAR(50)    NULL,
    requested_at            DATETIME(6)    NULL,
    approved_by             VARCHAR(50)    NULL,
    approved_at             DATETIME(6)    NULL,
    rejected_by             VARCHAR(50)    NULL,
    rejected_at             DATETIME(6)    NULL,
    reject_reason           VARCHAR(500)   NULL,
    created_at              DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at              DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by              VARCHAR(50)    NULL,
    updated_by              VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_adjustments_company_code UNIQUE (company_id, code),
    CONSTRAINT uk_stock_adjustments_company_session UNIQUE (company_id, session_id),
    CONSTRAINT fk_stock_adjustments_session FOREIGN KEY (session_id) REFERENCES stocktake_sessions (id),
    CONSTRAINT fk_stock_adjustments_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    INDEX idx_stock_adjustments_company_status (company_id, status),
    INDEX idx_stock_adjustments_company_warehouse (company_id, warehouse_id)
) ENGINE = InnoDB;

CREATE TABLE stock_adjustment_lines (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    company_id          BIGINT         NOT NULL,
    adjustment_id       BIGINT         NOT NULL,
    stocktake_line_id   BIGINT         NULL,
    bin_location_id     BIGINT         NOT NULL,
    product_id          BIGINT         NOT NULL,
    lot_no              VARCHAR(50)    NULL,
    system_quantity     DECIMAL(18, 3) NOT NULL,
    counted_quantity    DECIMAL(18, 3) NOT NULL,
    difference_quantity DECIMAL(18, 3) NOT NULL,
    quantity_before     DECIMAL(18, 3) NULL,
    quantity_after      DECIMAL(18, 3) NULL,
    reason              VARCHAR(255)   NULL,
    created_at          DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at          DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by          VARCHAR(50)    NULL,
    updated_by          VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_adjustment_lines_adjustment FOREIGN KEY (adjustment_id) REFERENCES stock_adjustments (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_adjustment_lines_stocktake_line FOREIGN KEY (stocktake_line_id) REFERENCES stocktake_lines (id) ON DELETE SET NULL,
    CONSTRAINT fk_stock_adjustment_lines_bin FOREIGN KEY (bin_location_id) REFERENCES bin_locations (id),
    INDEX idx_stock_adjustment_lines_company (company_id),
    INDEX idx_stock_adjustment_lines_adjustment (adjustment_id)
) ENGINE = InnoDB;
