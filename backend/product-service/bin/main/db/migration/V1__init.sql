-- product_db: danh mục sản phẩm - danh mục, đơn vị tính, sản phẩm (SKU, barcode).
-- Quy ước: mỗi bảng thực thể có 4 cột audit created_at, updated_at (UTC), created_by, updated_by.
-- Không khai báo DEFAULT CHARSET để bảng dùng charset/collation mặc định của database.

CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_code UNIQUE (code)
) ENGINE = InnoDB;

-- Đơn vị tính (Unit of Measure)
CREATE TABLE uoms (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    code VARCHAR(20)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_uoms_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE products (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    sku         VARCHAR(64)   NOT NULL,
    barcode     VARCHAR(64)   NULL,
    name        VARCHAR(255)  NOT NULL,
    description VARCHAR(1000) NULL,
    category_id BIGINT        NULL,
    uom_id      BIGINT        NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT uk_products_barcode UNIQUE (barcode),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_uom FOREIGN KEY (uom_id) REFERENCES uoms (id),
    INDEX idx_products_name (name),
    INDEX idx_products_category (category_id)
) ENGINE = InnoDB;
