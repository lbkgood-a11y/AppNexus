CREATE TABLE bw_coil_partial_return (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '按卷退回凭证兼幂等 UUID',
 handover_id BINARY(16) NOT NULL COMMENT '原整车交接 UUID',
 custody_evidence_id BINARY(16) NOT NULL COMMENT '本批实际复接管凭证 UUID',
 title_evidence_id BINARY(16) NOT NULL COMMENT '恢复原货主账的权利依据 UUID',
 restored_quota DECIMAL(30,12) NOT NULL COMMENT '本批冲回原实测三级已出量',
 restored_stock DECIMAL(30,12) NOT NULL COMMENT '本批恢复原账面库存及货权量',
 unit_id BINARY(16) NOT NULL COMMENT '原交接基础重量单位 UUID',
 request_digest CHAR(64) NOT NULL COMMENT '本批实际卷及凭证的稳定摘要',
 created_by BINARY(16) NULL COMMENT '可信退回过账人 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 过账时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,id,handover_id), UNIQUE(tenant_id,custody_evidence_id),
 FOREIGN KEY(tenant_id,handover_id) REFERENCES bw_coil_handover(tenant_id,id),
 CHECK(restored_quota>0 AND restored_stock>0)
) COMMENT='同一整车交接可分多批按实际退回卷过账';

CREATE TABLE bw_coil_partial_return_line (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 return_id BINARY(16) NOT NULL COMMENT '所属按卷退回批次 UUID',
 handover_id BINARY(16) NOT NULL COMMENT '对应的原交接 UUID',
 allocation_id BINARY(16) NOT NULL COMMENT '原交接卷明细的配货 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '实际返回并恢复的原库存身份 UUID',
 location_id BINARY(16) NOT NULL COMMENT '复接管时核对的原库位 UUID',
 restored_quota DECIMAL(30,12) NOT NULL COMMENT '按原实测冲回的额度量',
 restored_stock DECIMAL(30,12) NOT NULL COMMENT '按原账面恢复的库存及权利量',
 quality_evidence_id BINARY(16) NULL COMMENT '本次返仓合格凭证 UUID，空为待检',
 quality_released_at TIMESTAMP(6) NULL COMMENT '本次返仓质量放行的 UTC 时间',
 quality_released_by BINARY(16) NULL COMMENT '可信返仓质检放行人 UUID',
 created_by BINARY(16) NULL COMMENT '可信退回过账人 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 过账时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,return_id,receipt_id), UNIQUE(tenant_id,handover_id,allocation_id),
 FOREIGN KEY(tenant_id,return_id,handover_id) REFERENCES bw_coil_partial_return(tenant_id,id,handover_id),
 FOREIGN KEY(tenant_id,handover_id,allocation_id) REFERENCES bw_coil_handover_line(tenant_id,handover_id,allocation_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id),
 CHECK(restored_quota>0 AND restored_stock>0),
 CHECK((quality_evidence_id IS NULL AND quality_released_at IS NULL)
    OR (quality_evidence_id IS NOT NULL AND quality_released_at IS NOT NULL))
) COMMENT='原交接卷只允许退回一次，独立留存本次返仓质检';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0143-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:return_coils',
  'warehousing.outbound.coil','return_coils','登记按卷退回','Post returned coils','返仓待检过账及按原口径冲回'),
 (UUID_TO_BIN('019a65d0-0143-0000-8000-000000000002'),'TENANT','warehousing.outbound.coil:release_returned_quality',
  'warehousing.outbound.coil','release_returned_quality','放行返仓卷','Release returned coil quality','按本次返仓卷的合格依据转出待检');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission
 WHERE code IN('warehousing.outbound.coil:return_coils','warehousing.outbound.coil:release_returned_quality');
