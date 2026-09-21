CREATE TABLE IF NOT EXISTS inventories (
    id VARCHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(36) NOT NULL UNIQUE,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    reserved_stock INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
