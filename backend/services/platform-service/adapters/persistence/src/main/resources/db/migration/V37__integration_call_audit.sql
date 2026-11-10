-- 外部调用审计不保存请求正文、密码、Token、IP 原文或业务字段原值。
CREATE TABLE integration_call_audit (
    -- 追加事件标识，只用于审计排序。
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 固定绑定的权威租户 UUID，不能从请求参数选择。
    tenant_id BINARY(16) NOT NULL,
    -- 已认证客户端标识，不保存密钥。
    client_id VARCHAR(100) NOT NULL,
    -- 稳定操作代码，不使用原始 URL 记录用户输入。
    operation VARCHAR(100) NOT NULL,
    -- 平台生成的 32 位追踪标识，可与技术日志关联。
    trace_id VARCHAR(32) NOT NULL,
    -- HTTP 结果状态码，仅表示调用结果。
    status INT NOT NULL,
    -- 服务端 UTC 时间，数据库自动写入。
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_call_audit_tenant (tenant_id,id),
    INDEX ix_call_audit_trace (trace_id)
);
