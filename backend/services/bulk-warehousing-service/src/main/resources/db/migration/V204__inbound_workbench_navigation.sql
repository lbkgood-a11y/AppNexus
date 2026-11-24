-- 工作台菜单仅引用现有队列权限，卡片和命令仍使用各自已登记的统一操作。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
    permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0204-7000-8000-000000000001'),id,'MENU',
    'builtin.navigation.warehousing.inbound-workbench','入库工作台','Inbound workbench',
    '/warehousing/inbound-workbench','warehousing/inbound-workbench','ri/file-list-line',
    'warehousing.inboundtask:read_workbench_queue',2,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY';
