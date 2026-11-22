ALTER TABLE bw_outbound_trip
 ADD COLUMN retired_at TIMESTAMP(6) NULL COMMENT '实际退回或授权已关闭时终止原车次的 UTC 时间';

-- 原无名唯一索引为 tenant_id。保留原外键的前缀索引，唯一性改为仅针对有效交接。
ALTER TABLE bw_coil_handover ADD INDEX idx_bw_handover_trip(tenant_id,trip_id);
ALTER TABLE bw_coil_handover
 DROP INDEX tenant_id,
 ADD COLUMN reversed_at TIMESTAMP(6) NULL COMMENT '原交接被完整补偿的 UTC 提交时间',
 ADD COLUMN active_trip_id BINARY(16) GENERATED ALWAYS AS
  (CASE WHEN reversed_at IS NULL THEN trip_id ELSE NULL END) STORED COMMENT '未冲销的车次交接唯一键',
 ADD UNIQUE KEY uk_bw_active_handover_trip(tenant_id,active_trip_id);

ALTER TABLE bw_coil_handover_line ADD INDEX idx_bw_handover_line_receipt(tenant_id,receipt_id);
ALTER TABLE bw_coil_handover_line
 DROP INDEX tenant_id,
 ADD COLUMN reversed_at TIMESTAMP(6) NULL COMMENT '原卷已补偿的 UTC 提交时间',
 ADD COLUMN active_receipt_id BINARY(16) GENERATED ALWAYS AS
  (CASE WHEN reversed_at IS NULL THEN receipt_id ELSE NULL END) STORED COMMENT '未冲销的出库卷唯一键',
 ADD UNIQUE KEY uk_bw_active_handover_receipt(tenant_id,active_receipt_id);

CREATE TABLE bw_coil_return_acceptance (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '实际退回验收 UUID',
 handover_id BINARY(16) NOT NULL COMMENT '原真实出仓交接 UUID',
 custody_evidence_id BINARY(16) NOT NULL COMMENT '已重新接管凭证 UUID',
 title_evidence_id BINARY(16) NOT NULL COMMENT '恢复原货主账的权利依据 UUID',
 quality_evidence_id BINARY(16) NOT NULL COMMENT '返仓卷质量放行依据 UUID',
 request_digest CHAR(64) NOT NULL COMMENT '退回范围和凭证的稳定请求摘要',
 created_by BINARY(16) NULL COMMENT '可信验收账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 验收入档时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,handover_id), UNIQUE(tenant_id,custody_evidence_id),
 FOREIGN KEY(tenant_id,handover_id) REFERENCES bw_coil_handover(tenant_id,id)
) COMMENT='实际出仓后整车返仓的独立验收及三类凭证';

CREATE TABLE bw_coil_return_line (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 return_id BINARY(16) NOT NULL COMMENT '实际退回验收 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '实际重新接管的整卷库存 UUID',
 location_id BINARY(16) NOT NULL COMMENT '确认返还的原主数据库位 UUID',
 created_by BINARY(16) NULL COMMENT '可信验收账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 验收入档时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,return_id,receipt_id),
 FOREIGN KEY(tenant_id,return_id) REFERENCES bw_coil_return_acceptance(tenant_id,id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id)
) COMMENT='实际退回的逐卷库位及库存标识';

CREATE TABLE bw_coil_handover_reversal (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '补偿流水兼幂等业务键 UUID',
 handover_id BINARY(16) NOT NULL COMMENT '唯一被补偿的交接 UUID',
 kind VARCHAR(24) NOT NULL COMMENT '误确认或真实退回',
 evidence_id BINARY(16) NOT NULL COMMENT '冲销核实及批准依据 UUID',
 return_id BINARY(16) NULL COMMENT '真实退回必须引用实际复接管验收 UUID',
 restored_quota DECIMAL(30,12) NOT NULL COMMENT '冲回原实测的三级已出重量',
 restored_stock DECIMAL(30,12) NOT NULL COMMENT '恢复原账面的库存及货权重量',
 restored_reservation DECIMAL(30,12) NOT NULL COMMENT '恢复原配货锁时转回的三级占用量',
 unit_id BINARY(16) NOT NULL COMMENT '上述重量采用的原交接基础单位 UUID',
 request_digest CHAR(64) NOT NULL COMMENT '稳定冲销请求摘要',
 created_by BINARY(16) NULL COMMENT '可信冲销批准账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 冲销提交时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,handover_id), UNIQUE(tenant_id,evidence_id),
 FOREIGN KEY(tenant_id,handover_id) REFERENCES bw_coil_handover(tenant_id,id),
 FOREIGN KEY(tenant_id,return_id) REFERENCES bw_coil_return_acceptance(tenant_id,id),
 CHECK((kind='ACTUAL_RETURN' AND return_id IS NOT NULL AND restored_reservation=0)
    OR (kind='MISTAKEN_HANDOVER' AND return_id IS NULL AND restored_reservation IN(0,restored_quota))),
 CHECK(restored_stock>0 AND restored_quota>0)
) COMMENT='按原出仓两种重量分别补偿的整车冲销';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0142-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:accept_return',
  'warehousing.outbound.coil','accept_return','验收实际退回','Accept returned coils','登记原整卷实际重新接管和验收'),
 (UUID_TO_BIN('019a65d0-0142-0000-8000-000000000002'),'TENANT','warehousing.outbound.coil:reverse_handover',
  'warehousing.outbound.coil','reverse_handover','冲销出仓交接','Reverse outbound handover','根据有效凭证补偿原交接及核销');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission
 WHERE code IN('warehousing.outbound.coil:accept_return','warehousing.outbound.coil:reverse_handover');
