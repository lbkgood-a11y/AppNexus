-- 仓储作业使用独立的租户一级目录，目录由可见子菜单决定是否展示。
-- 系统迁移无可信操作账号；审计账号为 NULL，时间由数据库按 UTC 生成。
INSERT INTO platform_menu(id,type,code,title_zh,title_en,icon,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
VALUES(UUID_TO_BIN('019a65d0-0163-7000-8000-000000000001'),'DIRECTORY','builtin.navigation.warehousing',
    '仓储管理','Warehouse management','ri/store-2-line',45,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6));

-- 仅迁移仓储作业入口，保留原页面标识、路由、授权及显隐状态。
UPDATE platform_menu child
JOIN platform_menu folder ON folder.code = 'builtin.navigation.warehousing'
    AND folder.scope_type = 'TENANT' AND folder.type = 'DIRECTORY'
SET child.parent_id = folder.id,
    child.sort_order = CASE child.code
        WHEN 'builtin.navigation.warehousing.coil-title' THEN 1
        WHEN 'builtin.navigation.warehousing.coil-flow' THEN 2
        WHEN 'builtin.navigation.warehousing.return-transit' THEN 3
    END,
    child.version = child.version + 1, child.updated_by = NULL, child.updated_at = CURRENT_TIMESTAMP(6)
WHERE child.scope_type = 'TENANT' AND child.type = 'MENU'
    AND child.code IN ('builtin.navigation.warehousing.coil-title',
        'builtin.navigation.warehousing.coil-flow', 'builtin.navigation.warehousing.return-transit');
