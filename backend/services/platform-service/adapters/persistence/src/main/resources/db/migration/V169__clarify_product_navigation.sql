-- 按类目、商品、货号的维护顺序更新内置租户菜单，保留页面身份、路由和授权。
-- 系统迁移没有可信操作账号，修改时间由数据库按 UTC 生成。
UPDATE platform_menu child
JOIN platform_menu folder ON child.parent_id = folder.id
SET child.title_zh = CASE child.code
        WHEN 'builtin.navigation.masterdata.product-category' THEN '商品类目'
        WHEN 'builtin.navigation.masterdata.product' THEN '商品（SPU）'
        WHEN 'builtin.navigation.masterdata.product.sku' THEN '货号管理（SKU）' END,
    child.title_en = CASE child.code
        WHEN 'builtin.navigation.masterdata.product-category' THEN 'Product categories'
        WHEN 'builtin.navigation.masterdata.product' THEN 'Products (SPU)'
        WHEN 'builtin.navigation.masterdata.product.sku' THEN 'SKU management' END,
    child.sort_order = CASE child.code
        WHEN 'builtin.navigation.masterdata.product-category' THEN 1
        WHEN 'builtin.navigation.masterdata.product' THEN 2
        WHEN 'builtin.navigation.masterdata.product.sku' THEN 3 END,
    child.version = child.version + 1,
    child.updated_by = NULL,
    child.updated_at = CURRENT_TIMESTAMP(6)
WHERE folder.code = 'builtin.navigation.masterdata.products'
  AND folder.type = 'DIRECTORY' AND folder.scope_type = 'TENANT'
  AND child.type = 'MENU' AND child.scope_type = 'TENANT'
  AND child.code IN ('builtin.navigation.masterdata.product-category',
                     'builtin.navigation.masterdata.product',
                     'builtin.navigation.masterdata.product.sku');
