CREATE TABLE IF NOT EXISTS auth_accounts (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL
);

-- Seed one local administrator only when this is a new, empty auth database.
INSERT INTO auth_accounts (id, email, password, status, role, created_at)
SELECT
    '00000000-0000-0000-0000-000000000001',
    'admin@flashticket.local',
    '$2y$12$sljbhkse5hEfTffYXikWQufWxdPBGzBqIQoFCsaxyO4p2Qi0sGxAO',
    'ACTIVE',
    'ADMIN',
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM auth_accounts);
