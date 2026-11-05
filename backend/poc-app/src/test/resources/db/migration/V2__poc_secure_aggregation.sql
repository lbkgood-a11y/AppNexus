CREATE TABLE member_fact (
    id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    company_id CHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_member_fact_scope (tenant_id, company_id, status)
);
