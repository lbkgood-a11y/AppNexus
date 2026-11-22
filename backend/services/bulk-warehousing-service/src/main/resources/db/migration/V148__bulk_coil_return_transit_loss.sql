CREATE TABLE bw_coil_return_transit_loss (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '批准短损的稳定业务 UUID',
 transit_id BINARY(16) NOT NULL COMMENT '原返仓发运 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '本次经复核的缺失原卷 UUID',
 responsible_partner_id BINARY(16) NOT NULL COMMENT '已确认责任方的主数据伙伴 UUID',
 evidence_id BINARY(16) NOT NULL COMMENT '复核及批准短损的依据 UUID',
 reason VARCHAR(256) NOT NULL COMMENT '经核准的短损原因',
 book_quantity DECIMAL(30,12) NOT NULL COMMENT '从原交接取的账面重量，供对账',
 measured_quantity DECIMAL(30,12) NOT NULL COMMENT '从原交接取的实测重量，供对账',
 request_digest CHAR(64) NOT NULL COMMENT '绑定缺失范围、责任及凭证的稳定请求摘要',
 created_by BINARY(16) NULL COMMENT '可信短损批准账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 批准短损结清时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,id,transit_id,receipt_id),
 FOREIGN KEY(tenant_id,transit_id,receipt_id) REFERENCES bw_coil_return_transit_line(tenant_id,transit_id,receipt_id),
 CHECK(book_quantity>0 AND measured_quantity>0)
) COMMENT='按卷批准短损结清，只记责任及凭证，不恢复任何仓储账';

ALTER TABLE bw_coil_return_transit_line
 ADD COLUMN active_loss_id BINARY(16) NULL COMMENT '当前生效的短损结清 UUID，补到获批重开后清空',
 ADD CONSTRAINT fk_bw_transit_line_active_loss FOREIGN KEY(tenant_id,active_loss_id,transit_id,receipt_id)
  REFERENCES bw_coil_return_transit_loss(tenant_id,id,transit_id,receipt_id),
 ADD CONSTRAINT ck_bw_transit_loss_receipt CHECK(active_loss_id IS NULL OR received_return_id IS NULL);

CREATE TABLE bw_coil_return_loss_reopening (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '补到复核重开的稳定业务 UUID',
 loss_id BINARY(16) NOT NULL COMMENT '原短损结清 UUID，每次短损只可重开一次',
 transit_id BINARY(16) NOT NULL COMMENT '原返仓发运 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '原短损卷 UUID',
 evidence_id BINARY(16) NOT NULL COMMENT '实际补到的核实与批准重开凭证 UUID',
 created_by BINARY(16) NULL COMMENT '可信补到复核账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 重开时间',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,loss_id),
 FOREIGN KEY(tenant_id,loss_id,transit_id,receipt_id) REFERENCES bw_coil_return_transit_loss(tenant_id,id,transit_id,receipt_id)
) COMMENT='补到后解除在途关闭，保留原短损并等待实际接管';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0148-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:settle_transit_loss',
  'warehousing.outbound.coil','settle_transit_loss','批准返仓短损结清','Settle return transit loss','经复核按原卷登记短损及责任'),
 (UUID_TO_BIN('019a65d0-0148-0000-8000-000000000002'),'TENANT','warehousing.outbound.coil:reopen_transit_loss',
  'warehousing.outbound.coil','reopen_transit_loss','批准短损补到重开','Reopen returned loss','补到凭证批准后重开原在途卷');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission
 WHERE code IN('warehousing.outbound.coil:settle_transit_loss','warehousing.outbound.coil:reopen_transit_loss');
