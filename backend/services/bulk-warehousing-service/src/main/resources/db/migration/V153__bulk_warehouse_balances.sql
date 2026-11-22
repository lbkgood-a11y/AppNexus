-- 定向查询先缩小至当前货主权利或原通知的仓库、货主及商品范围。
ALTER TABLE bw_inbound_title ADD INDEX idx_bw_title_owner_receipt(tenant_id,owner_id,receipt_id);
ALTER TABLE bw_outbound_notice ADD INDEX idx_bw_notice_owner_product(tenant_id,warehouse_id,owner_id,product_id,identity_id);

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0153-0000-8000-000000000001'),'TENANT','warehousing.coil.stockbalance:read',
  'warehousing.coil.stockbalance','read','查看在库货权','View on-hand title','查看指定货主及仓的在库货权和可提量'),
 (UUID_TO_BIN('019a65d0-0153-0000-8000-000000000002'),'TENANT','warehousing.outbound.coil:read_authorization_balances',
  'warehousing.outbound.coil','read_authorization_balances','查看提货授权余额','View pickup authorization balances','按货主及仓查询单据额度');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission
 WHERE code IN('warehousing.coil.stockbalance:read','warehousing.outbound.coil:read_authorization_balances');
