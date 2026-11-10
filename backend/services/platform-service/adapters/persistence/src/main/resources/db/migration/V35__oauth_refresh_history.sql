-- Refresh Token 历史仅保存摘要，用于检测旧 Token 重放；标准 Token 原文仍由框架 OAuth 仓储管理。
CREATE TABLE identity_refresh_history (
    -- SHA-256 摘要固定 32 字节，不作为 API 凭据返回。
    token_hash BINARY(32) NOT NULL PRIMARY KEY,
    -- 框架授权记录标识，每次设备授权独立，重放仅撤销对应授权。
    authorization_id VARCHAR(100) NOT NULL,
    -- 注册客户端内部标识，不能使用其他客户端的旧 Token 撤销目标设备。
    registered_client_id VARCHAR(100) NOT NULL,
    -- 族绝对到期时间，UTC；轮换不得延长设备授权寿命。
    expires_at TIMESTAMP(6) NOT NULL,
    INDEX ix_refresh_history_authorization (authorization_id)
);
