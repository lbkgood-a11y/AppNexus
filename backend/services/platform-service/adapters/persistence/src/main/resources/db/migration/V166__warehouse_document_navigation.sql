-- 仓储主单据按作业阶段分组。目录和入口仅引用现有读权限，迁移不创建授权。
-- 系统迁移无可信账号，审计账号为 NULL，数据库按 UTC 生成审计时间。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,icon,sort_order,scope_type,
    created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000001'),id,'DIRECTORY','builtin.navigation.warehousing.inbound',
    '入仓管理','Inbound management','ri/folder-line',10,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing' AND scope_type='TENANT' AND type='DIRECTORY';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
    permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000011'),id,'MENU',
    'builtin.navigation.warehousing.inbound-notices','入仓通知单','Inbound notices',
    '/warehousing/inbound-notices','warehousing/inbound-notices','ri/file-list-line',
    'warehousing.coil.stockbalance:read',1,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000012'),id,'MENU',
    'builtin.navigation.warehousing.inbound-receipts','入仓确认单','Inbound receipts',
    '/warehousing/inbound-receipts','warehousing/inbound-receipts','ri/file-list-line',
    'warehousing.coil.stockbalance:read',2,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000013'),id,'MENU',
    'builtin.navigation.warehousing.inbound-titles','入仓货权登记','Inbound title registrations',
    '/warehousing/inbound-titles','warehousing/inbound-titles','ri/file-list-line',
    'warehousing.coil.stockbalance:read',3,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000014'),id,'MENU',
    'builtin.navigation.warehousing.inbound-quality','入仓质量放行','Inbound quality releases',
    '/warehousing/inbound-quality','warehousing/inbound-quality','ri/file-list-line',
    'warehousing.coil.stockbalance:read',4,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,icon,sort_order,scope_type,
    created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000002'),id,'DIRECTORY','builtin.navigation.warehousing.title-control',
    '货权与监管','Title and stock control','ri/folder-line',11,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing' AND scope_type='TENANT' AND type='DIRECTORY';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
    permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000021'),id,'MENU',
    'builtin.navigation.warehousing.title-transfers','货权过户单','Title transfers',
    '/warehousing/title-transfers','warehousing/title-transfers','ri/file-list-line',
    'warehousing.coil.stockbalance:read',1,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.title-control' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000022'),id,'MENU',
    'builtin.navigation.warehousing.stock-restrictions','库存监管单','Stock restrictions',
    '/warehousing/stock-restrictions','warehousing/stock-restrictions','ri/file-list-line',
    'warehousing.stock.restriction:read',2,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.title-control' AND scope_type='TENANT' AND type='DIRECTORY';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,icon,sort_order,scope_type,
    created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000003'),id,'DIRECTORY','builtin.navigation.warehousing.outbound',
    '出仓管理','Outbound management','ri/folder-line',12,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing' AND scope_type='TENANT' AND type='DIRECTORY';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
    permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000031'),id,'MENU',
    'builtin.navigation.warehousing.outbound-notices','出仓通知单','Outbound notices',
    '/warehousing/outbound-notices','warehousing/outbound-notices','ri/file-list-line',
    'warehousing.coil.stockbalance:read',1,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.outbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000032'),id,'MENU',
    'builtin.navigation.warehousing.pickup-authorizations','提货授权单','Pickup authorizations',
    '/warehousing/pickup-authorizations','warehousing/pickup-authorizations','ri/file-list-line',
    'warehousing.outbound.coil:read_authorization_balances',2,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.outbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000033'),id,'MENU',
    'builtin.navigation.warehousing.outbound-trips','车次作业单','Outbound trips',
    '/warehousing/outbound-trips','warehousing/outbound-trips','ri/file-list-line',
    'warehousing.coil.stockbalance:read',3,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.outbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000034'),id,'MENU',
    'builtin.navigation.warehousing.outbound-confirmations','出仓确认单','Outbound confirmations',
    '/warehousing/outbound-confirmations','warehousing/outbound-confirmations','ri/file-list-line',
    'warehousing.coil.stockbalance:read',4,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.outbound' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000035'),id,'MENU',
    'builtin.navigation.warehousing.handover-reversals','出仓交接冲销单','Handover reversals',
    '/warehousing/handover-reversals','warehousing/handover-reversals','ri/file-list-line',
    'warehousing.coil.stockbalance:read',5,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.outbound' AND scope_type='TENANT' AND type='DIRECTORY';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,icon,sort_order,scope_type,
    created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000004'),id,'DIRECTORY','builtin.navigation.warehousing.returns',
    '返仓管理','Return management','ri/folder-line',13,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing' AND scope_type='TENANT' AND type='DIRECTORY';

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
    permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000041'),id,'MENU',
    'builtin.navigation.warehousing.return-dispatches','返仓发运单','Return dispatches',
    '/warehousing/return-dispatches','warehousing/return-dispatches','ri/file-list-line',
    'warehousing.outbound.coil:read_return_transit',1,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.returns' AND scope_type='TENANT' AND type='DIRECTORY'
UNION ALL
SELECT UUID_TO_BIN('019a65d0-0166-7000-8000-000000000042'),id,'MENU',
    'builtin.navigation.warehousing.return-confirmations','返仓确认单','Return confirmations',
    '/warehousing/return-confirmations','warehousing/return-confirmations','ri/file-list-line',
    'warehousing.outbound.coil:read_return_transit',2,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.returns' AND scope_type='TENANT' AND type='DIRECTORY';
