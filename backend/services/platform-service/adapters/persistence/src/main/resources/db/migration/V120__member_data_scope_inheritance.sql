-- 成员数据范围继承开关：历史授权保留独立范围，避免迁移时意外扩权。
ALTER TABLE access_member_authorization
  ADD COLUMN inherit_data_scope BOOLEAN NOT NULL DEFAULT FALSE
  COMMENT '是否持续继承角色当前数据范围：1继承，0成员自定义；新分配缺省1，历史迁移保留0';
-- 后续分配角色默认继承；旧记录不被此默认值改写。
ALTER TABLE access_member_authorization ALTER COLUMN inherit_data_scope SET DEFAULT TRUE;

-- 功能权限持续跟随角色；成员分支仍约束有效期，主体和租户状态由原视图复核。
-- 统一有效租户权限视图供管理权门禁复用；只投影主体与权限码，不暴露角色、范围或敏感资料。
CREATE OR REPLACE VIEW access_effective_tenant_permission AS
SELECT DISTINCT mr.tenant_id,mr.member_id,m.account_id,p.code permission_code
FROM access_member_role mr
JOIN tenant_member m ON m.tenant_id=mr.tenant_id AND m.id=mr.member_id
JOIN identity_account a ON a.id=m.account_id
JOIN platform_tenant t ON t.id=m.tenant_id
JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id
JOIN access_role_permission rp ON rp.role_id=r.id
JOIN access_permission p ON p.id=rp.permission_id
JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id
WHERE m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND t.enabled=TRUE
  AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.enabled=TRUE
  AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6));
