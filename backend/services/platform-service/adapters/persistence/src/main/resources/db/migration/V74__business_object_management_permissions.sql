-- 独立对象管理权限，替代运行时借用租户读取/组织授权权限；一次性迁移既有授权分支。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0074-7000-8000-000000000001'),'PLATFORM','business.object.catalog:manage','business.object.catalog','manage','管理业务对象目录','Manage business object catalog','维护对象目录、入口及租户可用范围'),
(UUID_TO_BIN('019a65d0-0074-7000-8000-000000000002'),'TENANT','business.object.configuration:manage','business.object.configuration','manage','管理业务配置','Manage business configuration','维护当前租户已开放对象的操作策略，不授予业务数据访问权');
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
SELECT rp.role_id,n.id,n.scope_type,rp.granted_by FROM access_role_permission rp JOIN access_permission old ON old.id=rp.permission_id
JOIN access_permission n ON (old.code='tenant:read' AND n.code='business.object.catalog:manage') OR (old.code='organization.permission:grant' AND n.code='business.object.configuration:manage');
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id)
SELECT ap.tenant_id,ap.member_id,ap.role_id,n.id FROM access_member_authorization_permission ap JOIN access_permission old ON old.id=ap.permission_id JOIN access_permission n ON n.code='business.object.configuration:manage' WHERE old.code='organization.permission:grant';
-- 菜单资源与门槛统一指向对象管理，不扩大其他资源权限。
UPDATE platform_menu SET permission_code='business.object.catalog:manage',version=version+1 WHERE component_key='platform/business-objects';
UPDATE platform_menu SET permission_code='business.object.configuration:manage',version=version+1 WHERE component_key='business-objects/index';
UPDATE platform_menu_resource r JOIN platform_menu m ON m.id=r.menu_id SET r.resource_code='business.object.catalog' WHERE m.component_key='platform/business-objects';
UPDATE platform_menu_resource r JOIN platform_menu m ON m.id=r.menu_id SET r.resource_code='business.object.configuration' WHERE m.component_key='business-objects/index';
-- 授权结构变化推进角色版本，拒绝迁移前已打开草稿的覆盖保存。
UPDATE access_role r SET r.version=r.version+1 WHERE EXISTS (
 SELECT 1 FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id
 WHERE rp.role_id=r.id AND p.code IN('business.object.catalog:manage','business.object.configuration:manage'));
