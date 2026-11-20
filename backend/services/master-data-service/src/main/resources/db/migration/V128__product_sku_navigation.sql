-- 商品明细独立入口复用商品读取与结构维护权限；不为既有成员增加授权，不创建平行SKU表。
-- 迁移由系统执行，四审计中的账号保持NULL，数据库生成UTC时间。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0128-7000-8000-000000000001'),parent_id,'MENU','builtin.navigation.masterdata.product.sku','商品明细管理','Product SKUs','/master-data/product-sku','master-data/product-sku','ri/barcode-line','masterdata.product:read',sort_order+1,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE component_key='master-data/product' AND scope_type='TENANT';
INSERT INTO platform_menu_resource(menu_id,resource_code,created_by,created_at,updated_by,updated_at)
SELECT id,'masterdata.product',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE component_key='master-data/product-sku' AND scope_type='TENANT';
