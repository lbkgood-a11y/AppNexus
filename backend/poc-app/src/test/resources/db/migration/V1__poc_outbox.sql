CREATE TABLE outbox_event (
    event_id CHAR(36) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    tenant_id CHAR(36) NULL,
    event_type VARCHAR(160) NOT NULL,
    contract_version INT NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    worker_id VARCHAR(120) NULL,
    leased_until TIMESTAMP(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error_code VARCHAR(120) NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT chk_outbox_scope_tenant CHECK (
        (scope = 'PLATFORM' AND tenant_id IS NULL)
        OR (scope = 'TENANT' AND tenant_id IS NOT NULL)
    ),
    INDEX idx_outbox_claim (status, next_attempt_at, leased_until, occurred_at)
);

CREATE TABLE event_consumption (
    event_id CHAR(36) NOT NULL,
    handler_id VARCHAR(160) NOT NULL,
    consumed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id, handler_id)
);
