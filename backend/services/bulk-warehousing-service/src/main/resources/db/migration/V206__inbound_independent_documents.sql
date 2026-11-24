-- 三张单据分别登记列表与详情权限。工作台和写操作沿用原授权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
 (UUID_TO_BIN('019a65d0-0206-7000-8000-000000000001'),'TENANT','warehousing.inboundquality:list_documents','warehousing.inboundquality','list_documents','查询质检单列表','List Quality orders','按可信租户读取单据，不返回车辆、司机或供应商'),
 (UUID_TO_BIN('019a65d0-0206-7000-8000-000000000002'),'TENANT','warehousing.inboundquality:read_document','warehousing.inboundquality','read_document','查询质检单详情','Read Quality orders','按可信租户读取单据，不返回车辆、司机或供应商'),
 (UUID_TO_BIN('019a65d0-0206-7000-8000-000000000003'),'TENANT','warehousing.inboundmetering:list_documents','warehousing.inboundmetering','list_documents','查询计量单（磅单）列表','List Metering orders','按可信租户读取单据，不返回车辆、司机或供应商'),
 (UUID_TO_BIN('019a65d0-0206-7000-8000-000000000004'),'TENANT','warehousing.inboundmetering:read_document','warehousing.inboundmetering','read_document','查询计量单（磅单）详情','Read Metering orders','按可信租户读取单据，不返回车辆、司机或供应商'),
 (UUID_TO_BIN('019a65d0-0206-7000-8000-000000000005'),'TENANT','warehousing.receipt:list_documents','warehousing.receipt','list_documents','查询入库单列表','List Receipt orders','按可信租户读取单据，不返回车辆、司机或供应商'),
 (UUID_TO_BIN('019a65d0-0206-7000-8000-000000000006'),'TENANT','warehousing.receipt:read_document','warehousing.receipt','read_document','查询入库单详情','Read Receipt orders','按可信租户读取单据，不返回车辆、司机或供应商');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission WHERE scope_type='TENANT' AND code IN ('warehousing.inboundquality:list_documents','warehousing.inboundquality:read_document','warehousing.inboundmetering:list_documents','warehousing.inboundmetering:read_document','warehousing.receipt:list_documents','warehousing.receipt:read_document');

-- 只为已获对应工作台卡片读取和 ALL 范围的现有系统管理员补齐独立读取。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,
 created_by,created_at,updated_by,updated_at)
SELECT rp.role_id,target.id,'TENANT',rp.granted_by,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission rp
JOIN access_role r ON r.id=rp.role_id AND r.scope_type='TENANT' AND r.code='sys_tenant_admin'
 AND r.role_type='SYSTEM' AND r.status='ENABLED' AND r.deleted_at IS NULL
JOIN access_permission source ON source.id=rp.permission_id
JOIN access_role_data_policy prior ON prior.role_id=rp.role_id AND prior.permission_id=source.id AND prior.scope_type='ALL'
JOIN access_permission target ON target.resource=source.resource AND target.scope_type='TENANT'
 AND target.action IN ('list_documents','read_document')
WHERE source.code IN ('warehousing.inboundquality:read_workbench_quality','warehousing.inboundmetering:read_workbench_meter','warehousing.receipt:read_workbench_receipt')
 AND NOT EXISTS(SELECT 1 FROM access_role_permission existing WHERE existing.role_id=rp.role_id AND existing.permission_id=target.id);
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,created_by,created_at,updated_by,updated_at)
SELECT rp.role_id,rp.permission_id,'ALL',p.resource,NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id
JOIN access_role r ON r.id=rp.role_id AND r.scope_type='TENANT' AND r.code='sys_tenant_admin'
 AND r.role_type='SYSTEM' AND r.status='ENABLED' AND r.deleted_at IS NULL
WHERE p.code IN ('warehousing.inboundquality:list_documents','warehousing.inboundquality:read_document','warehousing.inboundmetering:list_documents','warehousing.inboundmetering:read_document','warehousing.receipt:list_documents','warehousing.receipt:read_document')
 AND NOT EXISTS(SELECT 1 FROM access_role_data_policy d WHERE d.role_id=rp.role_id AND d.permission_id=rp.permission_id);

-- 通过公共授权版本使已有会话的授权缓存失效，保留单独配置的成员范围。
UPDATE access_role r
JOIN access_role_permission rp ON rp.role_id=r.id
JOIN access_permission p ON p.id=rp.permission_id
 AND p.id BETWEEN UUID_TO_BIN('019a65d0-0206-7000-8000-000000000001') AND UUID_TO_BIN('019a65d0-0206-7000-8000-000000000006')
SET r.version=r.version+1,r.updated_by=NULL,r.updated_at=CURRENT_TIMESTAMP(6)
WHERE r.scope_type='TENANT' AND r.code='sys_tenant_admin' AND r.role_type='SYSTEM' AND r.deleted_at IS NULL AND r.status='ENABLED';

UPDATE tenant_member m
JOIN access_member_authorization g ON g.tenant_id=m.tenant_id AND g.member_id=m.id
JOIN access_role r ON r.id=g.role_id AND r.tenant_id=g.tenant_id
JOIN access_role_permission rp ON rp.role_id=r.id
JOIN access_permission p ON p.id=rp.permission_id
 AND p.id BETWEEN UUID_TO_BIN('019a65d0-0206-7000-8000-000000000001') AND UUID_TO_BIN('019a65d0-0206-7000-8000-000000000006')
SET m.authority_version=m.authority_version+1
WHERE g.inherit_data_scope=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP(6))
 AND r.scope_type='TENANT' AND r.code='sys_tenant_admin' AND r.role_type='SYSTEM' AND r.deleted_at IS NULL AND r.status='ENABLED';

-- 限于原入仓目录，不覆盖工作台、通知单及其他菜单。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
 permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0206-7000-8000-000000000010'),id,'MENU',
 'builtin.navigation.warehousing.inbound-quality-orders','质检单','Quality orders','/warehousing/inbound-quality-orders','warehousing/inbound-quality-orders','ri/file-list-line',
 'warehousing.inboundquality:list_documents',3,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0206-7000-8000-000000000011'),id,'MENU',
 'builtin.navigation.warehousing.inbound-metering-orders','计量单（磅单）','Metering orders','/warehousing/inbound-metering-orders','warehousing/inbound-metering-orders','ri/file-list-line',
 'warehousing.inboundmetering:list_documents',4,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0206-7000-8000-000000000012'),id,'MENU',
 'builtin.navigation.warehousing.inbound-receipt-orders','入库单','Receipt orders','/warehousing/inbound-receipt-orders','warehousing/inbound-receipt-orders','ri/file-list-line',
 'warehousing.receipt:list_documents',5,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY';
