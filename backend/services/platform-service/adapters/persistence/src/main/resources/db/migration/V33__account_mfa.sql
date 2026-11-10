-- MFA 为全局账号认证资料，不属于租户业务数据；只允许本人认证流程访问。
CREATE TABLE identity_account_mfa (
    -- 不可变全局账号标识；每个账号最多一个 TOTP 因子。
    account_id BINARY(16) NOT NULL PRIMARY KEY,
    -- AES-256-GCM 密文包含随机 nonce；密钥来自部署密钥存储，不与密文同库。
    encrypted_secret VARBINARY(128) NOT NULL,
    -- 未确认绑定不得影响现有登录；必须验证一个有效 TOTP 后才能启用。
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- 待确认密钥的到期时间，UTC；默认由应用设为生成后 10 分钟。
    pending_until TIMESTAMP(6) NOT NULL,
    -- 已消费的最大 30 秒计数器，初始 -1；事务行锁阻止验证码并发重放。
    last_counter BIGINT NOT NULL DEFAULT -1,
    FOREIGN KEY (account_id) REFERENCES identity_account(id)
);
CREATE TABLE identity_mfa_recovery (
    -- 恢复码属于全局账号，不携带租户或业务权限。
    account_id BINARY(16) NOT NULL,
    -- 128 位随机恢复码的 SHA-256 摘要；原文只在激活成功时返回一次。
    code_hash BINARY(32) NOT NULL,
    PRIMARY KEY (account_id, code_hash),
    FOREIGN KEY (account_id) REFERENCES identity_account_mfa(account_id) ON DELETE CASCADE
);
