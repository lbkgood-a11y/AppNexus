-- 清除仅供旧组织授权入口使用的开发数据。普通租户角色和租户管理权不属于旧入口。
DELETE FROM organization_capability_grant;
DELETE FROM organization_grant_audit;

DELETE FROM access_member_permission
WHERE permission_code LIKE 'organization.unit:%';

-- 旧入口和历史迁移只为单项操作生成 LEGACY 系统角色；删除成员分配级联清理授权快照。
-- 保留角色标识并停用，避免既有审计或模板引用失效，也防止重新分配旧角色。
DELETE assignment
FROM access_member_role AS assignment
JOIN access_role AS role ON role.id = assignment.role_id AND role.tenant_id = assignment.tenant_id
JOIN access_role_permission AS grant_row ON grant_row.role_id = role.id
JOIN access_permission AS permission ON permission.id = grant_row.permission_id
WHERE role.role_type = 'SYSTEM' AND role.system_key LIKE 'LEGACY:%'
  AND permission.resource = 'organization.unit'
  AND NOT EXISTS (
      SELECT 1 FROM access_role_permission AS other_grant
      WHERE other_grant.role_id = role.id AND other_grant.permission_id <> grant_row.permission_id
  );

UPDATE access_role AS role
JOIN access_role_permission AS grant_row ON grant_row.role_id = role.id
JOIN access_permission AS permission ON permission.id = grant_row.permission_id
SET role.status = 'DISABLED', role.version = role.version + 1,
    role.updated_by = NULL, role.updated_at = CURRENT_TIMESTAMP(6)
WHERE role.role_type = 'SYSTEM' AND role.system_key LIKE 'LEGACY:%'
  AND role.status <> 'DISABLED' AND permission.resource = 'organization.unit'
  AND NOT EXISTS (
      SELECT 1 FROM access_role_permission AS other_grant
      WHERE other_grant.role_id = role.id AND other_grant.permission_id <> grant_row.permission_id
  );
