-- 工作流引擎和管理页面已完成组合装配与真实数据库验收，启用此前默认关闭的租户导航。
-- 本迁移只改变入口启用状态，不授予工作流权限；用户仍须通过角色获得对应操作权限。
UPDATE platform_menu
SET enabled=TRUE,version=version+1
WHERE component_key IN ('workflow/index','workflow/designer') AND scope_type='TENANT' AND enabled=FALSE;
