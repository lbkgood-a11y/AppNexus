CREATE TABLE bw_inbound_title (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '已过账实收范围 UUID',
 owner_id BINARY(16) NOT NULL COMMENT '主数据货主伙伴 UUID',
 evidence_id BINARY(16) NOT NULL COMMENT '经岗位核验的权利凭证 UUID',
 effective_at TIMESTAMP(6) NOT NULL COMMENT '凭证记载的 UTC 权利生效时点，区别于仓库登记时间',
 quantity DECIMAL(30,12) NOT NULL COMMENT '按实收基础单位登记的份额',
 created_by BINARY(16) NULL COMMENT '可信权利登记人 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '登记时点 UTC',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最后变更时点 UTC',
 PRIMARY KEY(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id),
 CHECK(quantity>0)
) COMMENT='已收实物货权登记，独立于入仓和质检';

CREATE TABLE bw_inbound_quality_release (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '判定覆盖的已过账实收范围 UUID',
 evidence_id BINARY(16) NOT NULL COMMENT '经质量岗位核验的合格证据 UUID',
 quantity DECIMAL(30,12) NOT NULL COMMENT '本次放行的实收数量，沿用库存基础单位',
 created_by BINARY(16) NULL COMMENT '可信质量放行人 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '批准及执行时点 UTC',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最后变更时点 UTC',
 PRIMARY KEY(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id),
 CHECK(quantity>0)
) COMMENT='实收范围质量放行事实及状态转换数量';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0137-0000-8000-000000000001'),'TENANT','warehousing.inbound.title:register','warehousing.inbound.title','register','登记入仓货权','Register inbound title','按实收范围登记已核验货权'),
 (UUID_TO_BIN('019a65d0-0137-0000-8000-000000000002'),'TENANT','warehousing.inbound.quality:release','warehousing.inbound.quality','release','批准质量放行','Release inbound quality','按实收范围批准质量状态转换');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE resource IN('warehousing.inbound.title','warehousing.inbound.quality') AND scope_type='TENANT';
