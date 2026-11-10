-- 工作流权限独立授权；不把平台身份或单据审批人自动提升为流程管理员。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0049-7000-8000-000000000001'),'TENANT','workflow:read','workflow','read','查看工作流','Read workflows','仅当前租户工作流能力，业务对象及字段权限仍由业务服务复核。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0049-7000-8000-000000000002'),'TENANT','workflow:edit','workflow','edit','编辑流程草稿','Edit workflow drafts','仅当前租户工作流能力，业务对象及字段权限仍由业务服务复核。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0049-7000-8000-000000000003'),'TENANT','workflow:publish','workflow','publish','发布和停用流程','Publish workflows','仅当前租户工作流能力，业务对象及字段权限仍由业务服务复核。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0049-7000-8000-000000000004'),'TENANT','workflow:start','workflow','start','发起流程','Start workflows','仅当前租户工作流能力，业务对象及字段权限仍由业务服务复核。');
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES(UUID_TO_BIN('019a65d0-0049-7000-8000-000000000005'),'TENANT','workflow:operate','workflow','operate','运行恢复管理','Operate workflows','仅当前租户工作流能力，业务对象及字段权限仍由业务服务复核。');
-- 按既有系统租户管理员规则补齐新能力，普通和自定义角色不自动扩权。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
 SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000)
 FROM access_role r JOIN access_permission p ON p.resource='workflow' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id)
 SELECT g.tenant_id,g.member_id,g.role_id,p.id FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id
 JOIN access_permission p ON p.resource='workflow' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
-- 默认停用导航：运行环境完成引擎迁移、开启引擎并注册真实业务能力后，由菜单管理员显式启用。
INSERT INTO platform_menu(id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type,enabled)
 VALUES(UUID_TO_BIN('019a65d0-0049-7000-8000-000000000010'),'MENU','builtin.navigation.workflow','工作流中心','Workflow center','/workflow/center','workflow/index','ri/flow-chart',NULL,20,'TENANT',FALSE);
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0049-7000-8000-000000000010'),'workflow');
UPDATE access_role SET version=version+1 WHERE system_key LIKE 'TENANT_ADMIN:%';
