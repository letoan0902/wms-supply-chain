-- inventory_db: kho, vị trí lưu trữ (zone/rack/bin) và tồn kho.
-- Quy ước: mỗi bảng thực thể có 4 cột audit created_at, updated_at (UTC), created_by, updated_by.
-- Không khai báo DEFAULT CHARSET để bảng dùng charset/collation mặc định của database.

CREATE TABLE warehouses (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    address    VARCHAR(255) NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_warehouses_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE bin_locations (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT      NOT NULL,
    zone         VARCHAR(20) NOT NULL,
    rack         VARCHAR(20) NOT NULL,
    bin          VARCHAR(20) NOT NULL,
    code         VARCHAR(80) NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bin_locations_code UNIQUE (code),
    CONSTRAINT uk_bin_locations_position UNIQUE (warehouse_id, zone, rack, bin),
    CONSTRAINT fk_bin_locations_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE = InnoDB;

-- product_id là tham chiếu logic tới product-service (khác database nên không có khóa ngoại)
CREATE TABLE stock_items (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    product_id        BIGINT         NOT NULL,
    bin_location_id   BIGINT         NOT NULL,
    lot_no            VARCHAR(50)    NULL,
    quantity          DECIMAL(18, 3) NOT NULL DEFAULT 0,
    reserved_quantity DECIMAL(18, 3) NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_items_bin FOREIGN KEY (bin_location_id) REFERENCES bin_locations (id),
    INDEX idx_stock_items_product (product_id),
    INDEX idx_stock_items_bin (bin_location_id)
) ENGINE = InnoDB;
