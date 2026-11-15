-- 管理功能引用既有平台租户管理和租户管理能力；不因菜单新增自动扩大普通角色权限。
INSERT INTO platform_menu(id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type)
 VALUES(UUID_TO_BIN('019a65d0-0061-7000-8000-000000000001'),'MENU','builtin.navigation.platform.documents','单据管理','Document registry','/platform/documents','platform/documents','ri/file-list-line','tenant:read',25,'PLATFORM'),
 (UUID_TO_BIN('019a65d0-0061-7000-8000-000000000002'),'MENU','builtin.navigation.documents','单据工作台','Document workbench','/documents/workbench','documents/index','ri/file-list-line','organization.permission:grant',26,'TENANT');
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES
 (UUID_TO_BIN('019a65d0-0061-7000-8000-000000000001'),'tenant'),
 (UUID_TO_BIN('019a65d0-0061-7000-8000-000000000002'),'organization.permission');
