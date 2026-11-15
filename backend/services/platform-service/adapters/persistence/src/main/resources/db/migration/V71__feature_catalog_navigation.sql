-- 功能清单只读展示构建内登记；复用菜单读取权限，不额外授予操作或租户业务权限。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type)
VALUES(UUID_TO_BIN('019a65d0-0071-7000-8000-000000000001'),UUID_TO_BIN('019a65d0-0027-7000-8000-000000000001'),'MENU','builtin.navigation.platform.features','功能清单','Feature catalog','/platform/features','platform/features','ri/list-check','configuration.menu:read',13,'PLATFORM');
-- 资源关系供现有导航授权匹配使用；不把入口可见性当成接口授权。
INSERT INTO platform_menu_resource(menu_id,resource_code)
VALUES(UUID_TO_BIN('019a65d0-0071-7000-8000-000000000001'),'configuration.menu');
