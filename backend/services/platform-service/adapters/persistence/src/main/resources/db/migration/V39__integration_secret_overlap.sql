-- 允许受控双密钥过渡，旧密钥仅保留哈希，重叠窗口固定 10 分钟。
ALTER TABLE integration_client
    -- 前一个客户端密钥的 Argon2id 哈希；无过渡密钥时为空，禁止存原文。
    ADD COLUMN previous_secret_hash VARCHAR(500) NULL,
    -- 旧密钥可用于认证的截止 UTC 时间，默认空表示不接受旧密钥。
    ADD COLUMN previous_secret_until TIMESTAMP(6) NULL;
