CREATE TABLE refresh_token_family (
    family_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    generation BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (family_id),
    INDEX idx_refresh_session (session_id, status)
);
