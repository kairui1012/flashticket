CREATE TABLE IF NOT EXISTS tickets (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    total_stock INT NOT NULL,
    sale_start_time DATETIME NOT NULL,
    sale_end_time DATETIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_tickets_event_id (event_id)
);
