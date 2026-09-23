CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    ticket_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_orders_user_created_at (user_id, created_at),
    INDEX idx_orders_ticket_id (ticket_id),
    INDEX idx_orders_status_expires_at (status, expires_at),
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_orders_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_orders_total_amount_non_negative CHECK (total_amount >= 0)
);
