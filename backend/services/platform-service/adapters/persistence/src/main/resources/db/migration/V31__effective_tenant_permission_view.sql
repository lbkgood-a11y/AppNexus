-- 统一有效租户权限视图供管理权门禁复用；只投影主体与权限码，不暴露角色、范围或敏感资料。
CREATE VIEW access_effective_tenant_permission AS
SELECT DISTINCT mr.tenant_id,mr.member_id,m.account_id,p.code permission_code
FROM access_member_role mr
JOIN tenant_member m ON m.tenant_id=mr.tenant_id AND m.id=mr.member_id
JOIN identity_account a ON a.id=m.account_id
JOIN platform_tenant t ON t.id=m.tenant_id
JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id
JOIN access_role_permission rp ON rp.role_id=r.id
JOIN access_permission p ON p.id=rp.permission_id
JOIN access_member_authorization g ON g.tenant_id=mr.tenant_id AND g.member_id=mr.member_id AND g.role_id=mr.role_id
JOIN access_member_authorization_permission ap ON ap.tenant_id=g.tenant_id AND ap.member_id=g.member_id AND ap.role_id=g.role_id AND ap.permission_id=p.id
WHERE m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE AND t.enabled=TRUE
  AND r.status='ENABLED' AND r.deleted_at IS NULL AND p.enabled=TRUE
  AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6));
