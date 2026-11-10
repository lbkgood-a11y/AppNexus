-- 合作方业务引用登记是实际写入场景，幂等记录与字典引用在同一数据库事务提交。
CREATE TABLE integration_reference_request (
    -- 客户端标识来自 Bearer 权威身份，不接受请求体指定。
    client_id VARCHAR(100) NOT NULL,
    -- 幂等键仅在同客户端和本操作内唯一，16–128 位受限 ASCII 字符。
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    -- 规范化请求字段的 SHA-256 摘要，键复用而参数不同时返回 409。
    request_hash BINARY(32) NOT NULL,
    -- 对外引用登记标识，不复用内部字典项主键。
    reference_id BINARY(16) NOT NULL,
    -- 可信固定租户，防止请求或查询跨租户。
    tenant_id BINARY(16) NOT NULL,
    -- 内部字典项标识，响应不返回。
    item_id BINARY(16) NOT NULL,
    -- 调用方业务引用键，最长 200 字符，禁止填入敏感原值。
    reference_key VARCHAR(200) NOT NULL,
    -- 服务端 UTC 创建时间，用于后续保留与清理策略。
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (client_id,idempotency_key),
    UNIQUE KEY uk_partner_reference_id (reference_id),
    INDEX ix_partner_reference_tenant (tenant_id,client_id)
);
