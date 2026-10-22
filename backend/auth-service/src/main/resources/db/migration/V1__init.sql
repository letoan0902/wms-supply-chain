-- auth_db: tài khoản người dùng và phân quyền theo vai trò (RBAC).
-- Quy ước: mỗi bảng thực thể có 4 cột audit created_at, updated_at (UTC), created_by, updated_by.
-- Không khai báo DEFAULT CHARSET để bảng dùng charset/collation mặc định của database.

CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

-- Sáu vai trò của hệ thống (khớp vn.wms.common.constant.Roles)
INSERT INTO roles (name, description, created_by, updated_by) VALUES
    ('ADMIN', 'System administrator', 'system', 'system'),
    ('WAREHOUSE_MANAGER', 'Warehouse manager', 'system', 'system'),
    ('WAREHOUSE_STAFF', 'Warehouse staff', 'system', 'system'),
    ('PURCHASING', 'Purchasing staff', 'system', 'system'),
    ('SALES', 'Sales staff', 'system', 'system'),
    ('ACCOUNTANT', 'Accountant', 'system', 'system');
