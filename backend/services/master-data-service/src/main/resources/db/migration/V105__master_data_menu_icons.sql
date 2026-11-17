-- 主数据子菜单按业务含义区分图标；历史迁移保持不变，采用已注册的本地 Remix Icon 名称。
-- 仅更新内置租户菜单的图标，保留权限、层级、排序及创建审计；系统迁移无可信账号，修改账号置 NULL。
-- 版本与 UTC 修改时间同语句推进；图标已相同则不重复更新。
UPDATE platform_menu SET icon='ri/contacts-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.partner' AND scope_type='TENANT' AND NOT (icon <=> 'ri/contacts-line');
UPDATE platform_menu SET icon='ri/store-2-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.warehouse' AND scope_type='TENANT' AND NOT (icon <=> 'ri/store-2-line');
UPDATE platform_menu SET icon='ri/global-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.countryregion' AND scope_type='TENANT' AND NOT (icon <=> 'ri/global-line');
UPDATE platform_menu SET icon='ri/map-pin-2-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.subdivision' AND scope_type='TENANT' AND NOT (icon <=> 'ri/map-pin-2-line');
UPDATE platform_menu SET icon='ri/shopping-bag-3-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.product' AND scope_type='TENANT' AND NOT (icon <=> 'ri/shopping-bag-3-line');
UPDATE platform_menu SET icon='ri/node-tree',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.product-category' AND scope_type='TENANT' AND NOT (icon <=> 'ri/node-tree');
UPDATE platform_menu SET icon='ri/ruler-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.product-unit' AND scope_type='TENANT' AND NOT (icon <=> 'ri/ruler-line');
UPDATE platform_menu SET icon='ri/list-settings-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.product-attribute' AND scope_type='TENANT' AND NOT (icon <=> 'ri/list-settings-line');
UPDATE platform_menu SET icon='ri/layout-grid-line',version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.masterdata.product-template' AND scope_type='TENANT' AND NOT (icon <=> 'ri/layout-grid-line');
