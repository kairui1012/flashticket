CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_transaction_id VARCHAR(100) NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    updated_at DATETIME NOT NULL,

    UNIQUE KEY uk_payments_order_id (order_id),
    UNIQUE KEY uk_payments_provider_transaction_id (provider_transaction_id),
    INDEX idx_payments_user_created_at (user_id, created_at),
    INDEX idx_payments_status_created_at (status, created_at),

    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0)
);
