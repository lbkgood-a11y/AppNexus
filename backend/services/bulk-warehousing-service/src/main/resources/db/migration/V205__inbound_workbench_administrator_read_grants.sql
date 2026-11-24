-- 只补齐仍有原仓储读取全范围的租户系统管理员，沿用原授权来源。
-- 不授予身份查看或单据写入权限；普通角色、定制数据范围、过期授权和撤权状态不扩大。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,
 created_by,created_at,updated_by,updated_at)
SELECT source.role_id,edit.id,'TENANT',source.granted_by,CURRENT_TIMESTAMP(6),
 NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission source
JOIN access_role role_row ON role_row.id=source.role_id AND role_row.scope_type='TENANT'
 AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM' AND role_row.deleted_at IS NULL AND role_row.status='ENABLED'
JOIN access_permission issue ON issue.id=source.permission_id AND issue.code='warehousing.coil.stockbalance:read'
JOIN access_permission edit ON edit.code IN ('warehousing.inboundtask:read_workbench_queue','warehousing.inboundtask:read_workbench_detail','warehousing.inboundquality:read_workbench_quality','warehousing.inboundmetering:read_workbench_meter','warehousing.receipt:read_workbench_receipt') AND edit.scope_type='TENANT'
WHERE EXISTS(SELECT 1 FROM access_role_data_policy prior WHERE prior.role_id=source.role_id AND prior.permission_id=issue.id AND prior.scope_type='ALL')
 AND NOT EXISTS(SELECT 1 FROM access_role_permission existing WHERE existing.role_id=source.role_id AND existing.permission_id=edit.id);

INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,
 created_by,created_at,updated_by,updated_at)
SELECT grant_row.role_id,grant_row.permission_id,'ALL',edit.resource,NULL,CURRENT_TIMESTAMP(6),
 NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission grant_row
JOIN access_role role_row ON role_row.id=grant_row.role_id AND role_row.scope_type='TENANT'
 AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM' AND role_row.deleted_at IS NULL AND role_row.status='ENABLED'
JOIN access_permission edit ON edit.id=grant_row.permission_id AND edit.code IN ('warehousing.inboundtask:read_workbench_queue','warehousing.inboundtask:read_workbench_detail','warehousing.inboundquality:read_workbench_quality','warehousing.inboundmetering:read_workbench_meter','warehousing.receipt:read_workbench_receipt')
WHERE NOT EXISTS(SELECT 1 FROM access_role_data_policy existing WHERE existing.role_id=grant_row.role_id AND existing.permission_id=grant_row.permission_id);

-- 提升受影响管理员角色及成员的授权版本，使公共授权缓存按原机制失效。
UPDATE access_role role_row JOIN access_role_permission grant_row ON grant_row.role_id=role_row.id
JOIN access_permission edit ON edit.id=grant_row.permission_id AND edit.code IN ('warehousing.inboundtask:read_workbench_queue','warehousing.inboundtask:read_workbench_detail','warehousing.inboundquality:read_workbench_quality','warehousing.inboundmetering:read_workbench_meter','warehousing.receipt:read_workbench_receipt')
SET role_row.version=role_row.version+1,role_row.updated_by=NULL,role_row.updated_at=CURRENT_TIMESTAMP(6)
WHERE role_row.scope_type='TENANT' AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM' AND role_row.deleted_at IS NULL AND role_row.status='ENABLED';

-- 继承角色范围的成员使用角色政策；不改写单独配置的成员快照。
UPDATE tenant_member member_row
JOIN access_member_authorization g ON g.tenant_id=member_row.tenant_id AND g.member_id=member_row.id
JOIN access_role role_row ON role_row.id=g.role_id AND role_row.tenant_id=g.tenant_id
JOIN access_role_permission grant_row ON grant_row.role_id=role_row.id
JOIN access_permission queue_permission ON queue_permission.id=grant_row.permission_id
 AND queue_permission.code='warehousing.inboundtask:read_workbench_queue'
SET member_row.authority_version=member_row.authority_version+1
WHERE g.inherit_data_scope=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6))
 AND role_row.scope_type='TENANT' AND role_row.code='sys_tenant_admin' AND role_row.role_type='SYSTEM'
 AND role_row.deleted_at IS NULL AND role_row.status='ENABLED';