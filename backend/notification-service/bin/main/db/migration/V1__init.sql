-- notification_db: nhật ký các thông báo (email cảnh báo) đã xử lý.
-- Quy ước: mỗi bảng thực thể có 4 cột audit created_at, updated_at (UTC), created_by, updated_by.
-- Không khai báo DEFAULT CHARSET để bảng dùng charset/collation mặc định của database.
-- status lưu dạng chuỗi: SENT hoặc FAILED

CREATE TABLE notification_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_type    VARCHAR(50)  NOT NULL,
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500) NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at  DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    created_by  VARCHAR(50) NULL,
    updated_by  VARCHAR(50) NULL,
    PRIMARY KEY (id),
    INDEX idx_notification_logs_status (status),
    INDEX idx_notification_logs_created (created_at)
) ENGINE = InnoDB;
