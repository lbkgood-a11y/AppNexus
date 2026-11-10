-- 将流程设计器独立为租户菜单，不授予新权限；read为入口门槛，编辑/发布继续逐操作授权。
-- 沿用工作流中心的启停状态，已启用环境无需再次人工开启；不存在原菜单时默认停用。
INSERT INTO platform_menu(id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type,enabled)
VALUES(UUID_TO_BIN('019a65d0-0050-7000-8000-000000000001'),'MENU','builtin.navigation.workflow.designer','流程设计器','Workflow designer','/workflow/designer','workflow/designer','ri/flow-chart','workflow:read',21,'TENANT',COALESCE((SELECT enabled FROM (SELECT enabled FROM platform_menu WHERE component_key='workflow/index' LIMIT 1) source_menu),FALSE));
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0050-7000-8000-000000000001'),'workflow');
