-- 登录选仓独立于仓库档案读取。仅登记按记录分配的范围，现有角色和成员不会自动获得该权限。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
 (UUID_TO_BIN('019a65d0-0212-7000-8000-000000000001'),'TENANT','masterdata.warehouse:select_login',
  'masterdata.warehouse','select_login','登录选仓','Select login warehouse','只允许当前成员选择获得授权且启用的仓库');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
VALUES (UUID_TO_BIN('019a65d0-0212-7000-8000-000000000001'),'RULES','masterdata.warehouse');
