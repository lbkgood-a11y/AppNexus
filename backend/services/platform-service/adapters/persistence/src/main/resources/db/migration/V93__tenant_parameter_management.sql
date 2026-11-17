-- 租户参数使用独立维护权限，不再借用组织授权权限；仅扩展系统租户管理员角色。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description,created_at,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0093-7000-8000-000000000001'),'TENANT','configuration.parameter:save','configuration.parameter','save','维护租户参数','Maintain tenant parameters','仅维护系统预定义的本租户非敏感参数，不授予组织授权能力。',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
-- 四字段审计账号保持 NULL；旧协议必填 granted_by 延用该角色已有授权来源，不伪造迁移账号。时间由数据库按 UTC 生成。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,created_at,updated_at)
SELECT r.id,p.id,'TENANT',prior.granted_by,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6) FROM access_role r JOIN (SELECT role_id,MIN(granted_by) granted_by FROM access_role_permission GROUP BY role_id) prior ON prior.role_id=r.id JOIN access_permission p ON p.code='configuration.parameter:save' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,created_at,updated_at)
SELECT g.tenant_id,g.member_id,g.role_id,p.id,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6) FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id JOIN access_permission p ON p.code='configuration.parameter:save' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
UPDATE tenant_member m SET authority_version=authority_version+1,updated_at=CURRENT_TIMESTAMP(6),updated_by=NULL WHERE EXISTS(SELECT 1 FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id WHERE g.tenant_id=m.tenant_id AND g.member_id=m.id AND r.system_key LIKE 'TENANT_ADMIN:%');
-- 默认挂载在已有系统配置目录；仍服从菜单发布及后端独立权限边界。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type,created_at,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0093-7000-8000-000000000002'),UUID_TO_BIN('019a65d0-0027-7000-8000-000000000004'),'MENU','builtin.navigation.configuration.parameters','租户参数','Tenant parameters','/configuration/parameters','configuration/parameters','ri/settings-3-line','configuration.parameter:save',19,'TENANT',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
INSERT INTO platform_menu_resource(menu_id,resource_code,created_at,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0093-7000-8000-000000000002'),'configuration.parameter',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
