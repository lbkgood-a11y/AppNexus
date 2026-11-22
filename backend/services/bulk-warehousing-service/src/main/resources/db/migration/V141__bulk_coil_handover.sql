ALTER TABLE bw_stock_receipt
 ADD COLUMN shipped_quantity DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '整卷已扣减的账面重量，入仓凭证原值保留',
 ADD CONSTRAINT ck_bw_stock_shipped CHECK(shipped_quantity>=0 AND pending_quantity+shipped_quantity<=inbound_quantity);

ALTER TABLE bw_inbound_title
 ADD COLUMN issued_quantity DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '该登记份额中已交出的账面重量',
 ADD CONSTRAINT ck_bw_title_issued CHECK(issued_quantity>=0 AND issued_quantity<=quantity);

CREATE TABLE bw_coil_handover (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '交接确认及幂等业务键 UUID',
 trip_id BINARY(16) NOT NULL COMMENT '车次受控交接范围 UUID',
 review_id BINARY(16) NOT NULL COMMENT '门岗采用的最终合格计量复核 UUID',
 evidence_id BINARY(16) NOT NULL COMMENT '门岗岗位确认的交接凭证 UUID',
 pickup_partner_id BINARY(16) NOT NULL COMMENT '实际核验的提货伙伴 UUID',
 issued_quantity DECIMAL(30,12) NOT NULL COMMENT '采用实测分摊的授权核销量',
 stock_deduction DECIMAL(30,12) NOT NULL COMMENT '同期出库的库存账面扣减重量',
 unit_id BINARY(16) NOT NULL COMMENT '上述重量共同采用的库存基础单位 UUID',
 request_digest CHAR(64) NOT NULL COMMENT '绑定实际交接范围及凭证的请求摘要',
 created_by BINARY(16) NULL COMMENT '可信门岗确认账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 交接提交时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,trip_id), UNIQUE(tenant_id,evidence_id),
 FOREIGN KEY(tenant_id,trip_id) REFERENCES bw_outbound_trip(tenant_id,id),
 FOREIGN KEY(tenant_id,review_id) REFERENCES bw_coil_weigh_review(tenant_id,id),
 CHECK(issued_quantity>0 AND stock_deduction>0)
) COMMENT='门岗已完成交接事实、实发核销及库存扣减摘要';

CREATE TABLE bw_coil_handover_line (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 handover_id BINARY(16) NOT NULL COMMENT '已提交的出仓交接 UUID',
 allocation_id BINARY(16) NOT NULL COMMENT '耗用的原配货及排他占用 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '实际交出整卷库存 UUID',
 issued_quantity DECIMAL(30,12) NOT NULL COMMENT '本卷经复核的实测核销重量',
 stock_deduction DECIMAL(30,12) NOT NULL COMMENT '本卷库存、货权账面扣减重量',
 created_by BINARY(16) NULL COMMENT '可信门岗确认账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 实发确认时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,handover_id,allocation_id), UNIQUE(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,handover_id) REFERENCES bw_coil_handover(tenant_id,id),
 FOREIGN KEY(tenant_id,allocation_id) REFERENCES bw_coil_allocation(tenant_id,id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id),
 CHECK(issued_quantity>0 AND stock_deduction>0)
) COMMENT='已完成整卷交接流水，库存身份只允许发出一次';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES (UUID_TO_BIN('019a65d0-0141-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:handover',
'warehousing.outbound.coil','handover','门岗确认交接','Confirm outbound handover','复核并确认实际交接、库存扣减及授权核销');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE code='warehousing.outbound.coil:handover';
