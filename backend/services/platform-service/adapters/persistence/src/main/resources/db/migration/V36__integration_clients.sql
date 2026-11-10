-- 外部集成主体独立于账号/成员，固定绑定一个租户，不通过系统账号冒充用户。
CREATE TABLE integration_client (
    -- OAuth 内部注册标识，与框架客户端注册一一对应。
    id VARCHAR(100) NOT NULL PRIMARY KEY,
    -- 不可变租户绑定，授权及 Token 必须复核租户启用状态。
    tenant_id BINARY(16) NOT NULL,
    -- 管理员可读名称，1–100 字符，不影响客户端标识。
    name VARCHAR(100) NOT NULL,
    -- 默认启用；停用及授权修改推进版本，使已签发 Token 下一请求失效。
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- 单调递增授权版本，初始零；客户端不能指定。
    authority_version INT NOT NULL DEFAULT 0,
    -- 创建时间，UTC，仅用于管理排序及审计关联。
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_integration_tenant (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES platform_tenant(id)
);
CREATE TABLE integration_client_audit (
    -- 追加审计标识，不作为认证凭据。
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 平台操作者不可变 UUID，不保存姓名、密码或 Token。
    actor_id BINARY(16) NOT NULL,
    -- 不透明集成客户端标识，不是客户端密钥。
    client_id VARCHAR(100) NOT NULL,
    -- 稳定操作代码，不保存请求正文。
    action VARCHAR(40) NOT NULL,
    -- 操作发生时间，UTC，由数据库写入。
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
