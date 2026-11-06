-- 平台级账号安全日志，不属于租户业务日志；查询必须限制认证账号。
-- ID 仅用于日志分页排序，不作为业务聚合标识或安全凭据。
CREATE TABLE account_security_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BINARY(16) NOT NULL,
    action VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_security_log_account_id (account_id, id)
);
