-- V11__create_stocktake_sessions.sql: Đợt kiểm kê (Stocktake), đóng băng vị trí và chụp tồn hệ thống (WMS-14.1)
-- Ba bảng, đều có company_id ngay từ đầu (đa doanh nghiệp):
--   stocktake_sessions  : đợt kiểm kê của một kho, mã duy nhất theo (company_id, code).
--   stocktake_locations : các vị trí thuộc phạm vi đợt. Cột frozen_bin_location_id chỉ có giá trị khi vị trí đang
--                         bị đóng băng; ràng buộc duy nhất (company_id, frozen_bin_location_id) bảo đảm mỗi vị trí
--                         chỉ thuộc một đợt đang kiểm tại một thời điểm (MySQL cho phép nhiều giá trị NULL).
--   stocktake_lines     : dòng chụp tồn theo (vị trí, sản phẩm, số lô) và số đếm thực tế, chênh lệch.

CREATE TABLE stocktake_sessions (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    code                VARCHAR(30)   NOT NULL,
    warehouse_id        BIGINT        NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    scope_type          VARCHAR(20)   NOT NULL,
    scope_zones         VARCHAR(500)  NULL,
    scope_product_ids   VARCHAR(2000) NULL,
    planned_date        DATE          NOT NULL,
    notes               VARCHAR(500)  NULL,
    started_by          VARCHAR(50)   NULL,
    started_at          DATETIME(6)   NULL,
    finished_by         VARCHAR(50)   NULL,
    finished_at         DATETIME(6)   NULL,
    manager_approved_by VARCHAR(50)   NULL,
    manager_approved_at DATETIME(6)   NULL,
    approved_by         VARCHAR(50)   NULL,
    approved_at         DATETIME(6)   NULL,
    closed_by           VARCHAR(50)   NULL,
    closed_at           DATETIME(6)   NULL,
    close_reason        VARCHAR(500)  NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at          DATETIME(6)   NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by          VARCHAR(50)   NULL,
    updated_by          VARCHAR(50)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stocktake_sessions_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_stocktake_sessions_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    INDEX idx_stocktake_sessions_company_status (company_id, status),
    INDEX idx_stocktake_sessions_company_warehouse (company_id, warehouse_id),
    INDEX idx_stocktake_sessions_company_planned (company_id, planned_date)
) ENGINE = InnoDB;

CREATE TABLE stocktake_locations (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    company_id             BIGINT      NOT NULL,
    session_id             BIGINT      NOT NULL,
    bin_location_id        BIGINT      NOT NULL,
    frozen_bin_location_id BIGINT      NULL,
    created_at             DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at             DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by             VARCHAR(50) NULL,
    updated_by             VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stocktake_locations_session_bin UNIQUE (company_id, session_id, bin_location_id),
    CONSTRAINT uk_stocktake_locations_frozen_bin UNIQUE (company_id, frozen_bin_location_id),
    CONSTRAINT fk_stocktake_locations_session FOREIGN KEY (session_id) REFERENCES stocktake_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_stocktake_locations_bin FOREIGN KEY (bin_location_id) REFERENCES bin_locations (id),
    INDEX idx_stocktake_locations_session (session_id)
) ENGINE = InnoDB;

CREATE TABLE stocktake_lines (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    company_id               BIGINT         NOT NULL,
    session_id               BIGINT         NOT NULL,
    bin_location_id          BIGINT         NOT NULL,
    product_id               BIGINT         NOT NULL,
    lot_no                   VARCHAR(50)    NULL,
    system_quantity          DECIMAL(18, 3) NOT NULL DEFAULT 0,
    system_reserved_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0,
    counted_quantity         DECIMAL(18, 3) NULL,
    variance_quantity        DECIMAL(18, 3) NULL,
    count_times              INT            NOT NULL DEFAULT 0,
    last_counted_by          VARCHAR(50)    NULL,
    last_counted_at          DATETIME(6)    NULL,
    unexpected               BOOLEAN        NOT NULL DEFAULT FALSE,
    recount_requested        BOOLEAN        NOT NULL DEFAULT FALSE,
    reason                   VARCHAR(255)   NULL,
    created_at               DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at               DATETIME(6)    NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by               VARCHAR(50)    NULL,
    updated_by               VARCHAR(50)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stocktake_lines_session FOREIGN KEY (session_id) REFERENCES stocktake_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_stocktake_lines_bin FOREIGN KEY (bin_location_id) REFERENCES bin_locations (id),
    INDEX idx_stocktake_lines_company (company_id),
    INDEX idx_stocktake_lines_session_key (session_id, bin_location_id, product_id)
) ENGINE = InnoDB;
