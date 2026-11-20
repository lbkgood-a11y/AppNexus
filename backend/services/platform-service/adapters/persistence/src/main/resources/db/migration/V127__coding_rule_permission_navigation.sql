-- 编码规则使用独立租户权限；只扩展系统租户管理员，不把对象绑定权隐式扩展为规则维护权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description,created_at,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0127-7000-8000-000000000001'),'TENANT','coding.rule:manage','coding.rule','manage','管理编码规则','Manage coding rules','维护当前租户独立编码规则版本，不授予对象绑定或业务发号权限',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
-- 系统迁移无实际账号，审计账号保持NULL；旧授权来源列沿用角色原有来源。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,created_at,updated_at)
SELECT r.id,p.id,'TENANT',prior.granted_by,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
FROM access_role r JOIN (SELECT role_id,MIN(granted_by) granted_by FROM access_role_permission GROUP BY role_id) prior ON prior.role_id=r.id
JOIN access_permission p ON p.code='coding.rule:manage' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,created_at,updated_at)
SELECT g.tenant_id,g.member_id,g.role_id,p.id,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id JOIN access_permission p ON p.code='coding.rule:manage' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
UPDATE access_role SET version=version+1,updated_at=CURRENT_TIMESTAMP(6),updated_by=NULL WHERE system_key LIKE 'TENANT_ADMIN:%';
UPDATE tenant_member m SET authority_version=authority_version+1,updated_at=CURRENT_TIMESTAMP(6),updated_by=NULL
WHERE EXISTS(SELECT 1 FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id WHERE g.tenant_id=m.tenant_id AND g.member_id=m.id AND r.system_key LIKE 'TENANT_ADMIN:%');
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type,created_at,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0127-7000-8000-000000000002'),UUID_TO_BIN('019a65d0-0027-7000-8000-000000000004'),'MENU','builtin.navigation.coding.rules','编码规则','Coding rules','/configuration/coding-rules','configuration/coding-rules','ri/list-settings-line','coding.rule:manage',20,'TENANT',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
INSERT INTO platform_menu_resource(menu_id,resource_code,created_at,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0127-7000-8000-000000000002'),'coding.rule',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
