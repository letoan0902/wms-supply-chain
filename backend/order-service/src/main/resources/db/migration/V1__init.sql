-- order_db: nhà cung cấp, khách hàng, đơn mua hàng (PO) và đơn bán hàng (SO).
-- Quy ước: mỗi bảng thực thể có 4 cột audit created_at, updated_at (UTC), created_by, updated_by.
-- Không khai báo DEFAULT CHARSET để bảng dùng charset/collation mặc định của database.

CREATE TABLE suppliers (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    email      VARCHAR(100) NULL,
    phone      VARCHAR(30)  NULL,
    address    VARCHAR(255) NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_suppliers_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE customers (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    email      VARCHAR(100) NULL,
    phone      VARCHAR(30)  NULL,
    address    VARCHAR(255) NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_code UNIQUE (code)
) ENGINE = InnoDB;

-- status lưu dạng chuỗi: DRAFT, CONFIRMED, COMPLETED, CANCELLED
CREATE TABLE purchase_orders (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    po_number     VARCHAR(30)    NOT NULL,
    supplier_id   BIGINT         NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    order_date    DATE           NOT NULL,
    expected_date DATE           NULL,
    total_amount  DECIMAL(18, 2) NOT NULL DEFAULT 0,
    note          VARCHAR(500)   NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_orders_number UNIQUE (po_number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    INDEX idx_purchase_orders_status (status),
    INDEX idx_purchase_orders_supplier (supplier_id)
) ENGINE = InnoDB;

CREATE TABLE sales_orders (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    so_number     VARCHAR(30)    NOT NULL,
    customer_id   BIGINT         NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    order_date    DATE           NOT NULL,
    expected_date DATE           NULL,
    total_amount  DECIMAL(18, 2) NOT NULL DEFAULT 0,
    note          VARCHAR(500)   NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sales_orders_number UNIQUE (so_number),
    CONSTRAINT fk_sales_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    INDEX idx_sales_orders_status (status),
    INDEX idx_sales_orders_customer (customer_id)
) ENGINE = InnoDB;
