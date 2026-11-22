CREATE TABLE bw_stock_occupation (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '配货占用 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '被占用的已过账库存身份',
 released_at TIMESTAMP(6) NULL COMMENT '数据库记录 UTC 解除时点',
 active_receipt_id BINARY(16) GENERATED ALWAYS AS
   (CASE WHEN released_at IS NULL THEN receipt_id ELSE NULL END) STORED COMMENT '有效期间整笔库存唯一占用',
 created_by BINARY(16) NULL COMMENT '可信占用账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), UNIQUE(tenant_id,active_receipt_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id)
) COMMENT='整笔保管范围的配货占用及释放历史';

CREATE TABLE bw_stock_restriction (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '监管冻结指令 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '受限制的已过账库存身份',
 evidence_id BINARY(16) NOT NULL COMMENT '监管岗位核实的冻结凭证 UUID',
 release_evidence_id BINARY(16) NULL COMMENT '监管岗位核实的解除凭证 UUID，空表示仍冻结',
 created_by BINARY(16) NULL COMMENT '可信冻结账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信解除账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id), INDEX idx_bw_restriction_scope(tenant_id,receipt_id,release_evidence_id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id)
) COMMENT='库存范围冻结及解除凭证，多个有效冻结独立控制';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0138-0000-8000-000000000001'),'TENANT','warehousing.outbound.stock:check','warehousing.outbound.stock','check','校验可发','Check stock eligibility','核验出仓库存可执行范围'),
 (UUID_TO_BIN('019a65d0-0138-0000-8000-000000000002'),'TENANT','warehousing.outbound.stock:reserve','warehousing.outbound.stock','reserve','占用库存','Reserve stock','锁定符合门禁的保管范围'),
 (UUID_TO_BIN('019a65d0-0138-0000-8000-000000000003'),'TENANT','warehousing.outbound.stock:release','warehousing.outbound.stock','release','释放占用','Release reservation','按原占用解除排他控制'),
 (UUID_TO_BIN('019a65d0-0138-0000-8000-000000000004'),'TENANT','warehousing.stock.restriction:freeze','warehousing.stock.restriction','freeze','冻结库存','Freeze stock','登记独立监管限制'),
 (UUID_TO_BIN('019a65d0-0138-0000-8000-000000000005'),'TENANT','warehousing.stock.restriction:lift','warehousing.stock.restriction','lift','解除冻结','Lift stock restriction','按解除凭证处理指定监管限制');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE resource IN('warehousing.outbound.stock','warehousing.stock.restriction') AND scope_type='TENANT';
