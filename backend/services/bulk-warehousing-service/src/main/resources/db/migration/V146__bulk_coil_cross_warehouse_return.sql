ALTER TABLE bw_stock_receipt
 ADD COLUMN current_warehouse_id BINARY(16) NULL COMMENT '实际在库仓库 UUID，旧库存沿用原接管仓库',
 ADD CONSTRAINT ck_bw_current_stock_warehouse CHECK(current_warehouse_id IS NULL OR current_location_id IS NOT NULL);

ALTER TABLE bw_coil_handover_line
 ADD COLUMN source_warehouse_id BINARY(16) NULL COMMENT '该次实际出仓仓库 UUID，后续返仓不改变此事实';

-- 跨仓返仓上线前，原出仓仓库与其不可变的出仓通知仓库一致。
UPDATE bw_coil_handover_line l
 JOIN bw_coil_handover h ON h.tenant_id=l.tenant_id AND h.id=l.handover_id
 JOIN bw_outbound_trip v ON v.tenant_id=h.tenant_id AND v.id=h.trip_id
 JOIN bw_pickup_authorization a ON a.tenant_id=v.tenant_id AND a.id=v.authorization_id
 JOIN bw_outbound_notice n ON n.tenant_id=a.tenant_id AND n.id=a.notice_id
 SET l.source_warehouse_id=n.warehouse_id
 WHERE l.source_warehouse_id IS NULL;

ALTER TABLE bw_coil_handover_line
 MODIFY COLUMN source_warehouse_id BINARY(16) NOT NULL COMMENT '该次实际出仓仓库 UUID，后续返仓不改变此事实';

ALTER TABLE bw_coil_partial_return_line
 ADD COLUMN receiving_warehouse_id BINARY(16) NULL COMMENT '本批卷的实际接收仓库 UUID';

-- 历史退回只能回原仓，从原交接逐卷快照补齐接收仓。
UPDATE bw_coil_partial_return_line r
 JOIN bw_coil_handover_line l ON l.tenant_id=r.tenant_id AND l.handover_id=r.handover_id
   AND l.allocation_id=r.allocation_id
 SET r.receiving_warehouse_id=l.source_warehouse_id
 WHERE r.receiving_warehouse_id IS NULL;

ALTER TABLE bw_coil_partial_return_line
 MODIFY COLUMN receiving_warehouse_id BINARY(16) NOT NULL COMMENT '本批卷的实际接收仓库 UUID',
 MODIFY COLUMN location_id BINARY(16) NOT NULL COMMENT '实际返仓库位 UUID，属于接收仓库';

ALTER TABLE bw_stock_coil
 MODIFY COLUMN warehouse_id BINARY(16) NOT NULL COMMENT '库存当前所在仓库 UUID，仓内卷号保持唯一';
