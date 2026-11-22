ALTER TABLE bw_outbound_trip
 ADD INDEX idx_bw_trip_active_vehicle(tenant_id,vehicle_number,reserved),
 ADD COLUMN load_version BIGINT NOT NULL DEFAULT 0 COMMENT '卷集合或车辆限值变化时递增',
 ADD COLUMN review_id BINARY(16) NULL COMMENT '适用当前作业版本的最新复核 UUID',
 ADD COLUMN vehicle_limits_evidence_id BINARY(16) NULL COMMENT '车辆载重与总重限值核验凭证 UUID',
 ADD COLUMN payload_limit DECIMAL(30,12) NULL COMMENT '车辆允许货物净重上限，使用库存基础重量单位',
 ADD COLUMN gross_limit DECIMAL(30,12) NULL COMMENT '车辆允许总重上限，使用库存基础重量单位',
 ADD CONSTRAINT ck_bw_vehicle_limits CHECK(
   (payload_limit IS NULL AND gross_limit IS NULL AND vehicle_limits_evidence_id IS NULL)
   OR (payload_limit>0 AND gross_limit>=payload_limit AND vehicle_limits_evidence_id IS NOT NULL));

ALTER TABLE bw_coil_allocation
 ADD COLUMN measured_quantity DECIMAL(30,12) NULL COMMENT '最近批准的有效计量分摊重量',
 ADD CONSTRAINT ck_bw_allocation_measured CHECK(measured_quantity IS NULL OR measured_quantity>0);

CREATE TABLE bw_coil_weigh_review (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '复核业务键 UUID',
 trip_id BINARY(16) NOT NULL COMMENT '适用车次 UUID',
 ticket_id BINARY(16) NOT NULL COMMENT '计量岗位核验的有效原磅单 UUID',
 load_version BIGINT NOT NULL COMMENT '受检配货及限值版本',
 limits_evidence_id BINARY(16) NULL COMMENT '本次采用的限载依据 UUID',
 gross DECIMAL(30,12) NOT NULL COMMENT '整车毛重，使用库存基础重量单位',
 tare DECIMAL(30,12) NOT NULL COMMENT '车辆皮重，使用库存基础重量单位',
 net DECIMAL(30,12) NOT NULL COMMENT '确认净重，使用库存基础重量单位',
 state VARCHAR(16) NOT NULL COMMENT 'APPROVED 为批准，HELD 为拦截保留原占用',
 reasons VARCHAR(512) NOT NULL COMMENT '使用英文逗号分隔的稳定拦截原因，批准时空串',
 request_digest CHAR(64) NOT NULL COMMENT '固定证据及分摊的 SHA-256 摘要',
 created_by BINARY(16) NULL COMMENT '可信复核账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,ticket_id),
 FOREIGN KEY(tenant_id,trip_id) REFERENCES bw_outbound_trip(tenant_id,id),
 CHECK(tare>=0 AND net>0 AND gross=tare+net),
 CHECK(state IN('APPROVED','HELD'))
) COMMENT='历史复磅及车辆限载综合复核';

CREATE TABLE bw_coil_weigh_line (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 review_id BINARY(16) NOT NULL COMMENT '复核业务键 UUID',
 allocation_id BINARY(16) NOT NULL COMMENT '对应整卷配货明细 UUID',
 quantity DECIMAL(30,12) NOT NULL COMMENT '本次实测分摊，使用库存基础重量单位',
 created_by BINARY(16) NULL COMMENT '可信复核账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,review_id,allocation_id),
 FOREIGN KEY(tenant_id,review_id) REFERENCES bw_coil_weigh_review(tenant_id,id),
 FOREIGN KEY(tenant_id,allocation_id) REFERENCES bw_coil_allocation(tenant_id,id),
 CHECK(quantity>0)
) COMMENT='整车计量的实际整卷分摊';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0140-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:vehicle_limits','warehousing.outbound.coil','vehicle_limits','核验车辆限载','Verify vehicle limits','核实车辆载货及总重上限'),
 (UUID_TO_BIN('019a65d0-0140-0000-8000-000000000002'),'TENANT','warehousing.outbound.coil:review_weighing','warehousing.outbound.coil','review_weighing','复核出仓计量','Review outbound weighing','核对磅单、独立车辆限载和三级额度');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE code IN('warehousing.outbound.coil:vehicle_limits','warehousing.outbound.coil:review_weighing');
