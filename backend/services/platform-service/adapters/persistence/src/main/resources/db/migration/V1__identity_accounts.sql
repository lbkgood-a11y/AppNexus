-- Platform-level tables: authentication is global and has no tenant_id.
CREATE TABLE identity_account (
    id BINARY(16) NOT NULL PRIMARY KEY,
    username VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_identity_account_username UNIQUE (username)
);
-- Singleton bootstrap claim is committed atomically with the first account.
CREATE TABLE identity_bootstrap (
    slot TINYINT NOT NULL PRIMARY KEY,
    account_id BINARY(16) NOT NULL,
    CONSTRAINT ck_identity_bootstrap_slot CHECK (slot = 1)
);
