ALTER TABLE bw_stock_receipt
 ADD COLUMN current_area_id BINARY(16) NULL COMMENT '同仓返仓的实际库存库区，历史未变更时沿用入仓快照',
 ADD COLUMN current_location_id BINARY(16) NULL COMMENT '同仓返仓的实际库存库位，历史未变更时沿用入仓快照',
 ADD COLUMN current_warehouse_version BIGINT NULL COMMENT '实际位置解析采用的仓库版本',
 ADD COLUMN current_area_version BIGINT NULL COMMENT '实际位置解析采用的库区版本',
 ADD COLUMN current_location_version BIGINT NULL COMMENT '实际位置解析采用的库位版本',
 ADD CONSTRAINT ck_bw_current_stock_position CHECK(
   (current_area_id IS NULL AND current_location_id IS NULL AND current_warehouse_version IS NULL
    AND current_area_version IS NULL AND current_location_version IS NULL)
   OR (current_area_id IS NOT NULL AND current_location_id IS NOT NULL AND current_warehouse_version IS NOT NULL
    AND current_area_version IS NOT NULL AND current_location_version IS NOT NULL
    AND current_warehouse_version>=0 AND current_area_version>=0 AND current_location_version>=0));

ALTER TABLE bw_coil_handover_line
 ADD COLUMN source_location_id BINARY(16) NULL COMMENT '原交接时实际出仓库位 UUID，返仓不改变此事实';

-- V143 之前的出仓与返仓只支持原入仓库位，历史来源可从原接管链唯一回填。
UPDATE bw_coil_handover_line l
 JOIN bw_inbound_receipt r ON r.tenant_id=l.tenant_id AND r.id=l.receipt_id
 JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
 SET l.source_location_id=c.location_id
 WHERE l.source_location_id IS NULL;

ALTER TABLE bw_coil_handover_line
 MODIFY COLUMN source_location_id BINARY(16) NOT NULL COMMENT '原交接时实际出仓库位 UUID，返仓不改变此事实';

ALTER TABLE bw_coil_partial_return_line
 MODIFY COLUMN location_id BINARY(16) NOT NULL COMMENT '本次返仓实际库位 UUID，须属于原仓库';
