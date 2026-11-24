-- 存量卷号继续原样保留；新过账的钢铁 LPN 按当前库存仓、租户和卷号占用唯一槽位。
ALTER TABLE bw_stock_receipt
  ADD COLUMN active_inbound_lpn VARCHAR(64) GENERATED ALWAYS AS (
    IF(quality_state IS NOT NULL AND reversed_at IS NULL AND inbound_quantity>shipped_quantity,UPPER(serial),NULL)
  ) STORED COMMENT '新入库仍在库的 LPN 唯一槽位，忽略大小写，冲红或出完后为空',
  ADD COLUMN active_lpn_warehouse_id BINARY(16) GENERATED ALWAYS AS (
    COALESCE(current_warehouse_id,origin_warehouse_id)
  ) STORED COMMENT '新 LPN 的库存仓 UUID，位置未移动时沿用原仓',
  ADD UNIQUE KEY uk_bw_stock_active_inbound_lpn(tenant_id,active_lpn_warehouse_id,active_inbound_lpn);

-- 钢铁草稿为独立接单操作，过账共享既有入库过账权限。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0201-7000-8000-000000000001'),'TENANT','warehousing.receipt:create_steel_draft',
 'warehousing.receipt','create_steel_draft','创建钢铁入库草稿','Create steel receipt draft',
 '按钢铁证据和理重占比分摊、创建多 LPN 入库草稿');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.receipt:create_steel_draft';
