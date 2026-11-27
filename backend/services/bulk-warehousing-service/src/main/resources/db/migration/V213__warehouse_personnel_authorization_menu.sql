-- 仓库人员授权作为仓储目录的独立入口，沿用租户授权中心的准入权限。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
 permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0213-7000-8000-000000000001'),id,'MENU',
 'builtin.navigation.warehousing.personnel-authorization','仓库人员授权','Warehouse personnel authorization',
 '/warehousing/personnel-authorization','warehousing/personnel-authorization','ri/shield-user-line',
 'access.tenant.role:grant',90,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing' AND scope_type='TENANT' AND type='DIRECTORY';
