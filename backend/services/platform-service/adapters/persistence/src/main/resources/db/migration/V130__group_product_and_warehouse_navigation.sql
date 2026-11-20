-- 主数据导航按商品、仓库聚合；目录不声明权限，由既有叶子页面权限决定是否展示。
-- 仅调整内置租户入口的父节点和顺序，保留页面身份、路由、授权、显隐及启停状态。
-- 系统迁移没有可信操作账号，审计账号为NULL，审计时间由数据库按UTC生成。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,icon,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0130-7000-8000-000000000001'),id,'DIRECTORY','builtin.navigation.masterdata.products','商品资料','Product data','ri/shopping-bag-3-line',5,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.master-data' AND type='DIRECTORY' AND scope_type='TENANT';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,icon,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0130-7000-8000-000000000002'),id,'DIRECTORY','builtin.navigation.masterdata.warehouses','仓库资料','Warehouse data','ri/store-2-line',3,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.master-data' AND type='DIRECTORY' AND scope_type='TENANT';

UPDATE platform_menu child
JOIN platform_menu folder ON folder.code='builtin.navigation.masterdata.products' AND folder.scope_type='TENANT'
SET child.parent_id=folder.id,
    child.sort_order=CASE child.component_key
        WHEN 'master-data/product' THEN 1
        WHEN 'master-data/product-sku' THEN 2
        WHEN 'master-data/product-category' THEN 3
        WHEN 'master-data/product-unit' THEN 4
        WHEN 'master-data/product-attribute' THEN 5
        WHEN 'master-data/product-template' THEN 6 END,
    child.version=child.version+1,child.updated_by=NULL,child.updated_at=CURRENT_TIMESTAMP(6)
WHERE child.scope_type='TENANT' AND child.type='MENU'
  AND child.code IN ('builtin.navigation.masterdata.product','builtin.navigation.masterdata.product.sku',
    'builtin.navigation.masterdata.product-category','builtin.navigation.masterdata.product-unit',
    'builtin.navigation.masterdata.product-attribute','builtin.navigation.masterdata.product-template')
  AND child.component_key IN ('master-data/product','master-data/product-sku','master-data/product-category',
    'master-data/product-unit','master-data/product-attribute','master-data/product-template');

UPDATE platform_menu child
JOIN platform_menu folder ON folder.code='builtin.navigation.masterdata.warehouses' AND folder.scope_type='TENANT'
SET child.parent_id=folder.id,
    child.sort_order=CASE child.component_key WHEN 'master-data/warehouse' THEN 1 WHEN 'master-data/storage' THEN 2 END,
    child.version=child.version+1,child.updated_by=NULL,child.updated_at=CURRENT_TIMESTAMP(6)
WHERE child.scope_type='TENANT' AND child.type='MENU'
  AND child.code IN ('builtin.navigation.masterdata.warehouse','builtin.navigation.masterdata.storage')
  AND child.component_key IN ('master-data/warehouse','master-data/storage');
