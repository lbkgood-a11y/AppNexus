-- 品类模板配置使用既有表，新权限分别对应查询、保存草稿与发布。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
 (UUID_TO_BIN('019a65d0-0209-7000-8000-000000000001'),'TENANT','warehousing.commoditytemplate:list','warehousing.commoditytemplate','list','查询入库品类模板','List inbound templates','查询本租户品类配置'),
 (UUID_TO_BIN('019a65d0-0209-7000-8000-000000000002'),'TENANT','warehousing.commoditytemplate:save','warehousing.commoditytemplate','save','保存品类模板草稿','Save inbound template draft','保存本租户未发布配置'),
 (UUID_TO_BIN('019a65d0-0209-7000-8000-000000000003'),'TENANT','warehousing.commoditytemplate:publish','warehousing.commoditytemplate','publish','发布品类模板','Publish inbound template','发布新版本并停用原版本');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission WHERE resource='warehousing.commoditytemplate';

-- 既有全范围通知管理的租户系统管理员取得入口，普通角色按岗位配置。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,created_by,created_at,updated_by,updated_at)
SELECT r.id,p.id,'TENANT',rp.granted_by,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role r
JOIN access_role_permission rp ON rp.role_id=r.id
JOIN access_permission prior ON prior.id=rp.permission_id AND prior.code='warehousing.inbound:update'
JOIN access_role_data_policy d ON d.role_id=r.id AND d.permission_id=rp.permission_id AND d.scope_type='ALL'
CROSS JOIN access_permission p WHERE p.resource='warehousing.commoditytemplate'
 AND r.scope_type='TENANT' AND r.role_type='SYSTEM' AND r.code='sys_tenant_admin' AND r.status='ENABLED'
 AND r.deleted_at IS NULL;
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,created_by,created_at,updated_by,updated_at)
SELECT rp.role_id,rp.permission_id,'ALL',p.resource,NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id AND p.resource='warehousing.commoditytemplate';
UPDATE access_role r JOIN access_role_permission rp ON rp.role_id=r.id
 AND rp.permission_id=UUID_TO_BIN('019a65d0-0209-7000-8000-000000000001')
 SET r.version=r.version+1,r.updated_by=NULL,r.updated_at=CURRENT_TIMESTAMP(6);
UPDATE tenant_member m JOIN access_member_authorization a ON a.member_id=m.id AND a.tenant_id=m.tenant_id
 JOIN access_role_permission rp ON rp.role_id=a.role_id AND rp.permission_id=UUID_TO_BIN('019a65d0-0209-7000-8000-000000000001')
 SET m.authority_version=m.authority_version+1
 WHERE a.inherit_data_scope=TRUE AND (a.expires_at IS NULL OR a.expires_at>CURRENT_TIMESTAMP(6));

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
 permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0209-7000-8000-000000000010'),id,'MENU',
 'builtin.navigation.warehousing.inbound-templates','入库品类模板','Inbound commodity templates',
 '/warehousing/inbound-templates','warehousing/inbound-templates','ri/settings-3-line',
 'warehousing.commoditytemplate:list',6,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY';
