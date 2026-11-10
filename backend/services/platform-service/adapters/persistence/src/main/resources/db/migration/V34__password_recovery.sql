-- 密码找回凭证仅保存摘要；账号版本变化、重新签发或十分钟到期都会使旧凭证失效。
CREATE TABLE identity_password_recovery (
    -- 每个全局账号最多一个待消费重置凭证，不属于租户数据。
    account_id BINARY(16) NOT NULL PRIMARY KEY,
    -- 256 位随机凭证的 SHA-256 摘要，禁止保存凭证原文。
    token_hash BINARY(32) NOT NULL,
    -- 签发时凭据版本；改密、停用或强制下线后不得使用旧凭证。
    credential_version BIGINT NOT NULL,
    -- 凭证到期时间，UTC；应用设置为签发后 10 分钟。
    expires_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (account_id) REFERENCES identity_account(id)
);
