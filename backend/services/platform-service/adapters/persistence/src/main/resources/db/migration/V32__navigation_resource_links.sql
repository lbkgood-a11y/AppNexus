-- 页面与授权资源显式关联；授权工作台从数据库读取，不再根据组件路径或显示名称猜测资源。
CREATE TABLE platform_menu_resource (
 menu_id BINARY(16) NOT NULL COMMENT '平台菜单 UUID，必填；删除菜单时同步删除资源关联',
 resource_code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '统一权限目录资源码，必填；不是 URL、表名或前端角色字符串',
 PRIMARY KEY(menu_id,resource_code),
 CONSTRAINT fk_menu_resource_menu FOREIGN KEY(menu_id) REFERENCES platform_menu(id) ON DELETE CASCADE,
 CONSTRAINT ck_menu_resource_code CHECK(REGEXP_LIKE(resource_code,'^[a-z][a-z0-9.]*$','c'))
);

INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'tenant' FROM platform_menu WHERE component_key='platform/tenants';
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'tenant.member' FROM platform_menu WHERE component_key='platform/tenants';
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'identity.account' FROM platform_menu WHERE component_key='platform/users';
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'configuration.menu' FROM platform_menu WHERE component_key='platform/menus';
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'access.role' FROM platform_menu WHERE component_key IN('platform/roles','platform/authorization');
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'access.tenant.role' FROM platform_menu WHERE component_key IN('roles/index','roles/authorization');
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'organization.permission' FROM platform_menu WHERE component_key='roles/authorization';
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'organization.unit' FROM platform_menu WHERE component_key='organization/index';
INSERT INTO platform_menu_resource(menu_id,resource_code)
SELECT id,'configuration.dictionary' FROM platform_menu WHERE component_key='configuration/dictionaries';

-- 租户角色与授权中心使用稳定权限码；管理员角色已由受控迁移持有这些权限。
UPDATE platform_menu SET permission_code='access.tenant.role:read',version=version+1
 WHERE component_key='roles/index' AND scope_type='TENANT';
UPDATE platform_menu SET permission_code='access.tenant.role:grant',version=version+1
 WHERE component_key='roles/authorization' AND scope_type='TENANT';
