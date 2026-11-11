-- POC 原文入口不要求整模块权限：本人及当前任务授权由业务 API 逐次复核；创建提交另验 workflow:start。
-- 默认启用菜单只提供导航，不自动注册组织、授予权限或发布流程。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,sort_order,scope_type)
VALUES(UUID_TO_BIN('019a65d0-0058-7000-8000-000000000001'),
 UUID_TO_BIN('019a65d0-0054-7000-8000-000000000001'),'MENU','builtin.navigation.workflow.leave',
 '请假单 POC','Leave request POC','/workflow/leave','workflow/leave','ri/file-info-line',2,'TENANT');
INSERT INTO platform_menu_resource(menu_id,resource_code)
VALUES(UUID_TO_BIN('019a65d0-0058-7000-8000-000000000001'),'workflow');
