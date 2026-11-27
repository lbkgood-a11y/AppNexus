-- 将既有“组织与人员”目录移入租户主数据，沿用目录和子菜单标识、路由、权限绑定及启停设置。
-- 目录排序值 6 接在商品资料之后。迁移无操作账号，修改时间按数据库 UTC 会话生成。
UPDATE platform_menu AS folder
JOIN platform_menu AS master_data
  ON master_data.code = 'builtin.navigation.master-data'
 AND master_data.scope_type = 'TENANT'
 AND master_data.type = 'DIRECTORY'
SET folder.parent_id = master_data.id,
    folder.sort_order = 6,
    folder.version = folder.version + 1,
    folder.updated_by = NULL,
    folder.updated_at = CURRENT_TIMESTAMP(6)
WHERE folder.code = 'builtin.navigation.organization'
  AND folder.scope_type = 'TENANT'
  AND folder.type = 'DIRECTORY'
  AND NOT (folder.parent_id <=> master_data.id);
