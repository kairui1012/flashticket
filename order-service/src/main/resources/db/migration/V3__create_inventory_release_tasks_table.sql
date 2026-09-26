CREATE TABLE IF NOT EXISTS inventory_release_tasks (
     id VARCHAR(36) PRIMARY KEY,
     order_id VARCHAR(36) NOT NULL,
     release_id VARCHAR(36) NOT NULL,
     ticket_id VARCHAR(36) NOT NULL,
     user_id VARCHAR(36) NOT NULL,
     quantity INT NOT NULL,
     reason VARCHAR(30) NOT NULL,

     status VARCHAR(20) NOT NULL,
     retry_count INT NOT NULL DEFAULT 0,
     next_retry_at DATETIME NOT NULL,
     locked_at DATETIME NULL,
     last_error VARCHAR(500) NULL,

     created_at DATETIME NOT NULL,
     updated_at DATETIME NOT NULL,

     UNIQUE KEY uk_release_order_id (order_id),
     UNIQUE KEY uk_release_id (release_id),
     INDEX idx_release_retry (status, next_retry_at)
);
