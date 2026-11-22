CREATE TABLE bw_inbound_notice (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '已签发的单行入仓通知 UUID',
 warehouse_id BINARY(16) NOT NULL COMMENT '主数据仓库 UUID',
 owner_id BINARY(16) NOT NULL COMMENT '已核验货主伙伴 UUID',
 product_id BINARY(16) NOT NULL COMMENT '主数据父商品 UUID',
 identity_kind VARCHAR(16) NOT NULL COMMENT '商品、规格或 SKU 核算粒度',
 identity_id BINARY(16) NOT NULL COMMENT '所选商品身份 UUID',
 product_version BIGINT NOT NULL COMMENT '签发采用的父商品版本',
 base_unit_id BINARY(16) NOT NULL COMMENT '库存基础单位 UUID',
 approved_quantity DECIMAL(30,12) NOT NULL COMMENT '基础单位批准收货上限',
 received_quantity DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '基础单位累计已收量',
 created_by BINARY(16) NULL COMMENT '可信签发账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 CHECK(approved_quantity>0 AND received_quantity>=0 AND received_quantity<=approved_quantity)
) COMMENT='入仓执行通知及累计实收额度';

CREATE TABLE bw_inbound_custody (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '可独立定量的接管范围 UUID',
 notice_id BINARY(16) NOT NULL COMMENT '入仓通知 UUID',
 warehouse_id BINARY(16) NOT NULL COMMENT '接管仓库 UUID',
 area_id BINARY(16) NOT NULL COMMENT '接管库区 UUID',
 location_id BINARY(16) NOT NULL COMMENT '接管库位 UUID',
 warehouse_version BIGINT NOT NULL COMMENT '签认采用的仓库版本',
 area_version BIGINT NOT NULL COMMENT '签认采用的库区版本',
 location_version BIGINT NOT NULL COMMENT '签认采用的库位版本',
 evidence_id BINARY(16) NOT NULL COMMENT '现场接管证据 UUID',
 created_by BINARY(16) NULL COMMENT '可信现场签认账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 系统接管登记时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,evidence_id),
 FOREIGN KEY(tenant_id,notice_id) REFERENCES bw_inbound_notice(tenant_id,id)
) COMMENT='已受控实物接管记录，独立于定量入账';

CREATE TABLE bw_inbound_receipt (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '实收确认 UUID',
 custody_id BINARY(16) NOT NULL COMMENT '实际接管范围，每个范围一次实收',
 measurement_id BINARY(16) NOT NULL COMMENT '实收采用的数量凭证或独立分摊行 UUID',
 quantity DECIMAL(30,12) NOT NULL COMMENT '基础单位实收数量',
 unit_id BINARY(16) NOT NULL COMMENT '实收采用的库存单位 UUID',
 request_digest CHAR(64) NOT NULL COMMENT '幂等请求内容 SHA-256 摘要',
 quantity_evidence VARCHAR(1500) NOT NULL COMMENT '采用的数量单位及换算依据快照',
 created_by BINARY(16) NULL COMMENT '可信收货确认账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 确认时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,custody_id),
 UNIQUE(tenant_id,measurement_id),
 FOREIGN KEY(tenant_id,custody_id) REFERENCES bw_inbound_custody(tenant_id,id),
 CHECK(quantity>0)
) COMMENT='实收数量确认，质量判定单独维护';

CREATE TABLE bw_stock_receipt (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '来源确认 UUID，同时为该次待检库存身份',
 inbound_quantity DECIMAL(30,12) NOT NULL COMMENT '不可变的入仓流水数量，基础单位',
 pending_quantity DECIMAL(30,12) NOT NULL COMMENT '待检余额，基础单位',
 created_by BINARY(16) NULL COMMENT '可信库存过账账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 过账时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_inbound_receipt(tenant_id,id),
 CHECK(inbound_quantity>0 AND pending_quantity>=0 AND pending_quantity<=inbound_quantity)
) COMMENT='按实收范围登记的入仓流水及待检余额';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0136-0000-8000-000000000001'),'TENANT','warehousing.inbound:issue','warehousing.inbound','issue','签发入仓通知','Issue inbound notice','签发受控入仓执行范围'),
 (UUID_TO_BIN('019a65d0-0136-0000-8000-000000000002'),'TENANT','warehousing.inbound:take_custody','warehousing.inbound','take_custody','签认接管','Take custody','签认已实际控制的货物'),
 (UUID_TO_BIN('019a65d0-0136-0000-8000-000000000003'),'TENANT','warehousing.inbound:receive','warehousing.inbound','receive','确认实收并过账','Confirm and post receipt','确认数量及待检库存入账');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE resource='warehousing.inbound' AND scope_type='TENANT';
