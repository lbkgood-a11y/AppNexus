-- 实物库存总览使用独立读取权限，以便明确授权跨品类库存查询。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES (UUID_TO_BIN('019a65d0-0164-7000-8000-000000000001'),'TENANT',
 'warehousing.coil.stockbalance:read_overview','warehousing.coil.stockbalance','read_overview',
 '查看库存总览','Read inventory overview','查询当前租户有权查看的跨品类实物库存');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission WHERE code='warehousing.coil.stockbalance:read_overview';

-- 新入口沿用已有仓储目录、模板图标及权限控制，不修改既有菜单。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
 permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0164-7000-8000-000000000002'),id,'MENU',
 'builtin.navigation.warehousing.inventory-overview','库存总览','Inventory overview',
 '/warehousing/inventory-overview','warehousing/inventory-overview','ri/archive-line',
 'warehousing.coil.stockbalance:read_overview',0,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing' AND scope_type='TENANT' AND type='DIRECTORY';
