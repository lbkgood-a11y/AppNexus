CREATE TABLE bw_title_watermark (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 revision BIGINT NOT NULL DEFAULT 0 COMMENT '已提交权利变动的租户单调代次',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后更新账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后修改时点',
 PRIMARY KEY(tenant_id), CHECK(revision>=0)
) COMMENT='提货授权与过户共用的租户线性货权代次';

ALTER TABLE bw_inbound_title
 ADD COLUMN title_revision BIGINT NOT NULL DEFAULT 0 COMMENT '活动货权代次，初次登记为零';
ALTER TABLE bw_pickup_authorization
 ADD COLUMN title_watermark BIGINT NOT NULL DEFAULT 0 COMMENT '签发时可覆盖的最大货权代次，存量签发为零';

CREATE TABLE bw_coil_title_transfer (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '过户及幂等 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '不移动的整卷身份 UUID',
 warehouse_id BINARY(16) NOT NULL COMMENT '过户仓库 UUID',
 location_id BINARY(16) NOT NULL COMMENT '过户时库存实际库位 UUID',
 from_owner_id BINARY(16) NOT NULL COMMENT '转出货主伙伴 UUID',
 to_owner_id BINARY(16) NOT NULL COMMENT '转入货主伙伴 UUID',
 unit_id BINARY(16) NOT NULL COMMENT '原库存基础单位 UUID',
 quantity DECIMAL(30,12) NOT NULL COMMENT '按原库存账面的整卷货权数量',
 title_revision BIGINT NOT NULL COMMENT '提交后活动货权代次',
 evidence_id BINARY(16) NOT NULL COMMENT '经有权岗位核验的批准凭证 UUID',
 request_digest CHAR(64) NOT NULL COMMENT '过户范围、原版本和依据的摘要',
 created_by BINARY(16) NULL COMMENT '可信批准账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 过户时点',
 updated_by BINARY(16) NULL COMMENT '可信最后更新账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后修改时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,title_revision), UNIQUE(tenant_id,receipt_id,evidence_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_coil(tenant_id,receipt_id),
 CHECK(from_owner_id<>to_owner_id AND quantity>0 AND title_revision>0)
) COMMENT='货权转移凭证及保持不动的实物快照';

CREATE TABLE bw_coil_title_entry (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 transfer_id BINARY(16) NOT NULL COMMENT '原过户 UUID',
 direction VARCHAR(3) NOT NULL COMMENT 'OUT 转出或 IN 转入',
 owner_id BINARY(16) NOT NULL COMMENT '该分录货主 UUID',
 signed_quantity DECIMAL(30,12) NOT NULL COMMENT '转出为负、转入为正，单位取过户记录',
 created_by BINARY(16) NULL COMMENT '可信批准账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 分录时点',
 updated_by BINARY(16) NULL COMMENT '可信最后更新账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 最后修改时点',
 PRIMARY KEY(tenant_id,transfer_id,direction),
 FOREIGN KEY(tenant_id,transfer_id) REFERENCES bw_coil_title_transfer(tenant_id,id),
 CHECK((direction='IN' AND signed_quantity>0) OR (direction='OUT' AND signed_quantity<0))
) COMMENT='整卷过户的等量货权转出及转入分录';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0151-0000-8000-000000000001'),'TENANT','warehousing.coil.title:transfer',
 'warehousing.coil.title','transfer','同仓整卷过户','Transfer coil title','核验凭证后同仓转移整卷货权');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE code='warehousing.coil.title:transfer';
