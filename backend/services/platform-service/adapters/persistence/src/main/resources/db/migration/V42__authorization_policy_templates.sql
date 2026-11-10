-- 策略模板仅保存本管理域角色完整授权快照；应用仍通过现有角色授权用例复核，不直接写运行权限表。
CREATE TABLE access_policy_template (
    -- 模板不可变 UUID，不作为授权凭据。
    id BINARY(16) NOT NULL PRIMARY KEY,
    -- 平台域为零 UUID，租户域为真实租户 UUID，禁止跨域应用。
    owner_id BINARY(16) NOT NULL,
    -- 模板显示名，1–100 字符，不参与权限判定。
    name VARCHAR(100) NOT NULL,
    -- 当前完整功能/数据/字段策略 JSON，不含业务原值或敏感凭据。
    policy_json JSON NOT NULL,
    -- 创建操作者全局 UUID；模板不因此授予创建者任何业务权限。
    created_by BINARY(16) NOT NULL,
    -- 服务端 UTC 时间，记录快照来源。
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_policy_template_owner (owner_id,created_at)
);
