CREATE TABLE bw_coil_return_transit_reroute (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '已批准改单的稳定业务 UUID',
 transit_id BINARY(16) NOT NULL COMMENT '保留原计划的返仓发运 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '改仓的原卷库存身份 UUID',
 previous_warehouse_id BINARY(16) NOT NULL COMMENT '批准时的原有效接收仓 UUID',
 receiving_warehouse_id BINARY(16) NOT NULL COMMENT '经批准的新接收仓 UUID',
 evidence_id BINARY(16) NOT NULL COMMENT '批准改仓的凭证 UUID',
 reason VARCHAR(256) NOT NULL COMMENT '已核验的改仓原因',
 request_digest CHAR(64) NOT NULL COMMENT '原卷、接收仓及批准依据的稳定摘要',
 created_by BINARY(16) NULL COMMENT '可信改仓审批账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 改仓登记时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,id,transit_id,receipt_id),
 FOREIGN KEY(tenant_id,transit_id,receipt_id) REFERENCES bw_coil_return_transit_line(tenant_id,transit_id,receipt_id),
 CHECK(previous_warehouse_id<>receiving_warehouse_id)
) COMMENT='逐卷在途改仓追加记录，保留原发运及各次接收仓';

ALTER TABLE bw_coil_return_transit_line
 ADD COLUMN active_reroute_id BINARY(16) NULL COMMENT '该卷最新生效的改仓 UUID，空时沿用发运原接收仓',
 ADD CONSTRAINT fk_bw_transit_line_active_reroute FOREIGN KEY(tenant_id,active_reroute_id,transit_id,receipt_id)
  REFERENCES bw_coil_return_transit_reroute(tenant_id,id,transit_id,receipt_id);

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES (UUID_TO_BIN('019a65d0-0149-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:reroute_transit_coil',
 'warehousing.outbound.coil','reroute_transit_coil','批准返仓在途改仓','Reroute coil in return transit','按卷修改当前接收仓并保留批准依据');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE code='warehousing.outbound.coil:reroute_transit_coil';
