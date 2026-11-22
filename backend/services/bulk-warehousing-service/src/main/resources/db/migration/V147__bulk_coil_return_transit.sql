CREATE TABLE bw_coil_return_transit (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '实际返仓发运 UUID，重放沿用',
 handover_id BINARY(16) NOT NULL COMMENT '原出仓交接 UUID',
 receiving_warehouse_id BINARY(16) NOT NULL COMMENT '发运凭证确认的计划接收仓 UUID',
 evidence_id BINARY(16) NOT NULL COMMENT '实际发运凭证 UUID',
 request_digest CHAR(64) NOT NULL COMMENT '发运范围、接收仓和凭证的稳定请求摘要',
 created_by BINARY(16) NULL COMMENT '可信发运账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 实际发运登记时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,id,handover_id), UNIQUE(tenant_id,evidence_id),
 FOREIGN KEY(tenant_id,handover_id) REFERENCES bw_coil_handover(tenant_id,id)
) COMMENT='跨仓返仓实际发运凭证，按原交接卷登记在途';

CREATE TABLE bw_coil_return_transit_line (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 transit_id BINARY(16) NOT NULL COMMENT '实际返仓发运 UUID',
 handover_id BINARY(16) NOT NULL COMMENT '原交接 UUID',
 allocation_id BINARY(16) NOT NULL COMMENT '原交接卷明细的配货 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '实际发送的整卷库存身份 UUID',
 received_return_id BINARY(16) NULL COMMENT '实际接管批次 UUID，空表示未接管',
 received_at TIMESTAMP(6) NULL COMMENT 'UTC 实际接管过账时间',
 created_by BINARY(16) NULL COMMENT '可信发运账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 创建时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,transit_id,receipt_id), UNIQUE(tenant_id,handover_id,receipt_id),
 FOREIGN KEY(tenant_id,transit_id,handover_id) REFERENCES bw_coil_return_transit(tenant_id,id,handover_id),
 FOREIGN KEY(tenant_id,handover_id,allocation_id) REFERENCES bw_coil_handover_line(tenant_id,handover_id,allocation_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,received_return_id,handover_id) REFERENCES bw_coil_partial_return(tenant_id,id,handover_id),
 FOREIGN KEY(tenant_id,received_return_id,receipt_id) REFERENCES bw_coil_partial_return_line(tenant_id,return_id,receipt_id),
 CHECK((received_return_id IS NULL AND received_at IS NULL) OR (received_return_id IS NOT NULL AND received_at IS NOT NULL))
) COMMENT='原卷精准在途清单，接管与退回过账同时核销';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0147-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:dispatch_return',
  'warehousing.outbound.coil','dispatch_return','登记返仓发运','Dispatch coils for return','按原交接卷登记跨仓发运'),
 (UUID_TO_BIN('019a65d0-0147-0000-8000-000000000002'),'TENANT','warehousing.outbound.coil:read_return_transit',
  'warehousing.outbound.coil','read_return_transit','查看返仓在途','View return transit','查询原发运卷及实际接管批次');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission
 WHERE code IN('warehousing.outbound.coil:dispatch_return','warehousing.outbound.coil:read_return_transit');
