-- 清理已下线仓储单据和相关作业入口，沿用历史迁移以兼容既有 Flyway 校验和。
DELETE FROM platform_menu
WHERE scope_type = 'TENANT' AND type = 'MENU'
  AND component_key IN (
    'warehousing/inbound-entries',
    'warehousing/inbound-receipts',
    'warehousing/inbound-titles',
    'warehousing/inbound-quality',
    'warehousing/title-transfers',
    'warehousing/stock-restrictions',
    'warehousing/outbound-notices',
    'warehousing/pickup-authorizations',
    'warehousing/outbound-trips',
    'warehousing/outbound-confirmations',
    'warehousing/handover-reversals',
    'warehousing/return-dispatches',
    'warehousing/return-confirmations',
    'warehousing/coil-title',
    'warehousing/coil-flow',
    'warehousing/return-transit'
  );

-- 移除下线后的空目录，保留仍有租户配置的目录。
DELETE directory FROM platform_menu directory
LEFT JOIN platform_menu child ON child.parent_id = directory.id
WHERE directory.scope_type = 'TENANT' AND directory.type = 'DIRECTORY'
  AND directory.code IN ('builtin.navigation.warehousing.title-control',
    'builtin.navigation.warehousing.outbound', 'builtin.navigation.warehousing.returns')
  AND child.id IS NULL;

-- 先回收成员和角色的授权及范围关系，再移除权限目录；保留通知和库存总览的权限。
CREATE TEMPORARY TABLE retired_warehouse_permissions (PRIMARY KEY(id))
SELECT id, code FROM access_permission WHERE scope_type = 'TENANT' AND code IN (
    'warehousing.coil.title:transfer',
    'warehousing.inbound.quality:release',
    'warehousing.inbound.title:register',
    'warehousing.inbound:receive',
    'warehousing.inbound:take_custody',
    'warehousing.outbound.coil:accept_return',
    'warehousing.outbound.coil:allocate',
    'warehousing.outbound.coil:authorize',
    'warehousing.outbound.coil:cancel',
    'warehousing.outbound.coil:close',
    'warehousing.outbound.coil:dispatch_return',
    'warehousing.outbound.coil:handover',
    'warehousing.outbound.coil:identify',
    'warehousing.outbound.coil:issue',
    'warehousing.outbound.coil:plan',
    'warehousing.outbound.coil:pre_reserve',
    'warehousing.outbound.coil:read_authorization_balances',
    'warehousing.outbound.coil:read_return_transit',
    'warehousing.outbound.coil:release_returned_quality',
    'warehousing.outbound.coil:reopen_transit_loss',
    'warehousing.outbound.coil:reroute_transit_coil',
    'warehousing.outbound.coil:return_coils',
    'warehousing.outbound.coil:reverse_handover',
    'warehousing.outbound.coil:review_weighing',
    'warehousing.outbound.coil:settle_transit_loss',
    'warehousing.outbound.coil:vehicle_limits',
    'warehousing.outbound.stock:check',
    'warehousing.outbound.stock:release',
    'warehousing.outbound.stock:reserve',
    'warehousing.stock.restriction:freeze',
    'warehousing.stock.restriction:lift',
    'warehousing.stock.restriction:read'
);
DELETE target_row FROM access_member_authorization_permission target_row
JOIN retired_warehouse_permissions retired ON retired.id = target_row.permission_id;
DELETE target_row FROM access_role_permission target_row
JOIN retired_warehouse_permissions retired ON retired.id = target_row.permission_id;
DELETE target_row FROM access_member_permission target_row
JOIN retired_warehouse_permissions retired ON retired.code = target_row.permission_code;
DELETE target_row FROM access_permission_scope_type target_row
JOIN retired_warehouse_permissions retired ON retired.id = target_row.permission_id;
DELETE target_row FROM access_permission target_row
JOIN retired_warehouse_permissions retired ON retired.id = target_row.id;
DROP TEMPORARY TABLE retired_warehouse_permissions;

-- 仅删除退出运行的资源字段策略；入仓通知及库存总览的同名保留资源继续生效。
DELETE FROM access_role_field_security WHERE resource_type IN (
  'warehousing.inbound.title', 'warehousing.inbound.quality', 'warehousing.coil.title',
  'warehousing.outbound.coil', 'warehousing.outbound.stock', 'warehousing.stock.restriction'
);

-- 已下线对象的目录、租户授权和可编辑业务配置。历史审计记录保留供追溯。
CREATE TEMPORARY TABLE retired_warehouse_objects (
  object_type VARCHAR(64) NOT NULL PRIMARY KEY
);
INSERT INTO retired_warehouse_objects(object_type) VALUES
  ('BulkCoilOutbound'), ('BulkCoilTitleTransfer'), ('BulkInboundQualityRelease'),
  ('BulkInboundTitleRegistration'), ('BulkOutboundStock'), ('BulkStockRestriction');

DELETE target_row FROM business_object_page_relationships target_row
JOIN retired_warehouse_objects retired ON retired.object_type = target_row.object_type;
DELETE target_row FROM business_object_configuration target_row
JOIN retired_warehouse_objects retired ON retired.object_type = target_row.object_type;
DELETE target_row FROM business_object_configuration_version target_row
JOIN retired_warehouse_objects retired ON retired.object_type = target_row.object_type;
DELETE target_row FROM business_object_tenant_grant target_row
JOIN retired_warehouse_objects retired ON retired.object_type = target_row.object_type;
DELETE target_row FROM business_object_encoder target_row
JOIN retired_warehouse_objects retired ON retired.object_type = target_row.object_type;
DELETE target_row FROM business_object_code_counter target_row
JOIN retired_warehouse_objects retired ON retired.object_type = target_row.object_type;
DELETE target_row FROM business_object_type target_row
JOIN retired_warehouse_objects retired ON retired.object_type = target_row.object_type;
DROP TEMPORARY TABLE retired_warehouse_objects;
