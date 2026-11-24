-- 补齐旧租户系统管理员的新增编辑操作。只处理仍具有入仓签发授权的系统管理员角色。
-- 普通角色、已撤销签发权限的管理员、未保留签发快照的成员不产生新增授权。
-- granted_by 沿用原授权来源用于兼容既有非空字段；四字段审计账号为空表示本次系统迁移。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,
 created_by,created_at,updated_by,updated_at)
SELECT source.role_id,edit.id,'TENANT',source.granted_by,CURRENT_TIMESTAMP(6),
 NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission source
JOIN access_role role_row ON role_row.id=source.role_id AND role_row.scope_type='TENANT'
 AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM' AND role_row.deleted_at IS NULL
JOIN access_permission issue ON issue.id=source.permission_id AND issue.code='warehousing.inbound:issue'
JOIN access_permission edit ON edit.code='warehousing.inbound:update' AND edit.scope_type='TENANT'
WHERE NOT EXISTS(SELECT 1 FROM access_role_permission existing WHERE existing.role_id=source.role_id AND existing.permission_id=edit.id);

INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,
 created_by,created_at,updated_by,updated_at)
SELECT grant_row.role_id,grant_row.permission_id,'ALL','warehousing.inbound',NULL,CURRENT_TIMESTAMP(6),
 NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission grant_row
JOIN access_role role_row ON role_row.id=grant_row.role_id AND role_row.scope_type='TENANT'
 AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM' AND role_row.deleted_at IS NULL
JOIN access_permission edit ON edit.id=grant_row.permission_id AND edit.code='warehousing.inbound:update'
WHERE NOT EXISTS(SELECT 1 FROM access_role_data_policy existing WHERE existing.role_id=grant_row.role_id AND existing.permission_id=grant_row.permission_id);

INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,scope_type,resource_type,
 created_by,created_at,updated_by,updated_at)
SELECT source.tenant_id,source.member_id,source.role_id,edit.id,'ALL','warehousing.inbound',
 NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_member_authorization_permission source
JOIN access_permission issue ON issue.id=source.permission_id AND issue.code='warehousing.inbound:issue'
JOIN access_permission edit ON edit.code='warehousing.inbound:update' AND edit.scope_type='TENANT'
JOIN access_role role_row ON role_row.id=source.role_id AND role_row.tenant_id=source.tenant_id
 AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM' AND role_row.deleted_at IS NULL
JOIN access_role_permission permitted ON permitted.role_id=source.role_id AND permitted.permission_id=edit.id
WHERE NOT EXISTS(SELECT 1 FROM access_member_authorization_permission existing
 WHERE existing.tenant_id=source.tenant_id AND existing.member_id=source.member_id
 AND existing.role_id=source.role_id AND existing.permission_id=edit.id);

-- 提升受影响管理员角色及成员的授权版本，使公共授权缓存按原机制失效。
UPDATE access_role role_row JOIN access_role_permission grant_row ON grant_row.role_id=role_row.id
JOIN access_permission edit ON edit.id=grant_row.permission_id AND edit.code='warehousing.inbound:update'
SET role_row.version=role_row.version+1,role_row.updated_by=NULL,role_row.updated_at=CURRENT_TIMESTAMP(6)
WHERE role_row.scope_type='TENANT' AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM' AND role_row.deleted_at IS NULL;

UPDATE tenant_member member_row JOIN access_member_authorization_permission grant_row
 ON grant_row.tenant_id=member_row.tenant_id AND grant_row.member_id=member_row.id
JOIN access_role role_row ON role_row.id=grant_row.role_id AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM'
JOIN access_permission edit ON edit.id=grant_row.permission_id AND edit.code='warehousing.inbound:update'
SET member_row.authority_version=member_row.authority_version+1;
