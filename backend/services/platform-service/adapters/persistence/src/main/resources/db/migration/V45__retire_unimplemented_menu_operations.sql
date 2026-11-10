-- 菜单启停由编辑表单统一保存，实际校验 configuration.menu:update；没有独立启停或发布用例。
-- 停用未接通操作而不删除历史授权记录，防止配置看似生效但实际没有执行点。
UPDATE access_permission SET enabled=FALSE,version=version+1
WHERE scope_type='PLATFORM' AND code IN('configuration.menu:enable','configuration.menu:publish');
-- 明确实际编辑保存包含启停，新增根节点/下级均共享创建菜单能力。
UPDATE access_permission SET name_zh='编辑菜单（含启停）',name_en='Edit menus (including status)',
 description='编辑菜单资料及启停状态；不包含独立发布操作。',version=version+1
WHERE scope_type='PLATFORM' AND code='configuration.menu:update';
