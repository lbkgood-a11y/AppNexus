CREATE TABLE bw_outbound_warehouse_guard (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 warehouse_id BINARY(16) NOT NULL COMMENT '主数据仓库 UUID',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,warehouse_id)
) COMMENT='出仓控制栅栏';

CREATE TABLE bw_stock_coil (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '整卷对应的已过账库存身份',
 warehouse_id BINARY(16) NOT NULL COMMENT '原严控仓库 UUID',
 serial VARCHAR(64) NOT NULL COMMENT '仓内稳定卷号',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,receipt_id), UNIQUE(tenant_id,warehouse_id,serial),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_receipt(tenant_id,receipt_id)
) COMMENT='库存身份的仓内唯一卷号';

CREATE TABLE bw_outbound_notice (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '该聚合的业务 UUID',
 warehouse_id BINARY(16) NOT NULL COMMENT '发货仓库 UUID',
 owner_id BINARY(16) NOT NULL COMMENT '当前保管权利主体的主数据伙伴 UUID',
 product_id BINARY(16) NOT NULL COMMENT '控制范围的主数据商品 UUID',
 identity_id BINARY(16) NOT NULL COMMENT '同一库存核算口径的身份 UUID',
 unit_id BINARY(16) NOT NULL COMMENT '本单据基础重量单位 UUID',
 committed DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '已签发子授权承诺',
 cap DECIMAL(30,12) NOT NULL COMMENT '批准上限，使用库存基础重量单位',
 issued DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '已交接核销的重量',
 reserved DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '未核销有效占用重量',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 FOREIGN KEY(tenant_id,warehouse_id) REFERENCES bw_outbound_warehouse_guard(tenant_id,warehouse_id),
 CHECK(committed>=0 AND committed<=cap),
 CHECK(cap>0 AND issued>=0 AND reserved>=0 AND issued+reserved<=cap)
) COMMENT='出仓通知及共同吨额约束';

CREATE TABLE bw_pickup_authorization (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '该聚合的业务 UUID',
 notice_id BINARY(16) NOT NULL COMMENT '父通知 UUID',
 pickup_partner_id BINARY(16) NOT NULL COMMENT '获准提货的业务伙伴 UUID',
 expires_at TIMESTAMP(6) NOT NULL COMMENT 'UTC 授权截止时点',
 closed_at TIMESTAMP(6) NULL COMMENT '完成撤配并释放未用承诺的 UTC 时点',
 unassigned DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '尚未派车的已占授权重量',
 cap DECIMAL(30,12) NOT NULL COMMENT '批准上限，使用库存基础重量单位',
 issued DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '已交接核销的重量',
 reserved DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '未核销有效占用重量',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 FOREIGN KEY(tenant_id,notice_id) REFERENCES bw_outbound_notice(tenant_id,id),
 CHECK(unassigned>=0 AND unassigned<=reserved),
 CHECK(cap>0 AND issued>=0 AND reserved>=0 AND issued+reserved<=cap)
) COMMENT='提货授权及尚未派车占用';

CREATE TABLE bw_outbound_trip (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '该聚合的业务 UUID',
 authorization_id BINARY(16) NOT NULL COMMENT '所属提货授权 UUID',
 vehicle_number VARCHAR(32) NOT NULL COMMENT '现场核验的车牌或车辆业务号',
 cap DECIMAL(30,12) NOT NULL COMMENT '批准上限，使用库存基础重量单位',
 issued DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '已交接核销的重量',
 reserved DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '未核销有效占用重量',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 FOREIGN KEY(tenant_id,authorization_id) REFERENCES bw_pickup_authorization(tenant_id,id),
 CHECK(cap>0 AND issued>=0 AND reserved>=0 AND issued+reserved<=cap)
) COMMENT='车次授权配额行';

CREATE TABLE bw_coil_allocation (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '该聚合的业务 UUID',
 trip_id BINARY(16) NOT NULL COMMENT '目标车次授权配额行 UUID',
 receipt_id BINARY(16) NOT NULL COMMENT '唯一整卷库存身份',
 serial VARCHAR(64) NOT NULL COMMENT '配货时采用的卷号快照',
 quantity DECIMAL(30,12) NOT NULL COMMENT '由库存计量导出的整卷占用重量',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时点',
 updated_by BINARY(16) NULL COMMENT '可信最后变更账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 最后变更时点',
 PRIMARY KEY(tenant_id,id),
 FOREIGN KEY(tenant_id,id) REFERENCES bw_stock_occupation(tenant_id,id),
 FOREIGN KEY(tenant_id,trip_id) REFERENCES bw_outbound_trip(tenant_id,id),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES bw_stock_coil(tenant_id,receipt_id),
 CHECK(quantity>0)
) COMMENT='原配货与排他占用的不可变关联';

INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000001'),'TENANT','warehousing.outbound.coil:identify','warehousing.outbound.coil','identify','核验卷号','Identify coil','核验卷号，复用仓储库存及统一权限'),
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000002'),'TENANT','warehousing.outbound.coil:issue','warehousing.outbound.coil','issue','签发出仓通知','Issue outbound notice','签发出仓通知，复用仓储库存及统一权限'),
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000003'),'TENANT','warehousing.outbound.coil:authorize','warehousing.outbound.coil','authorize','签发提货授权','Authorize pickup','签发提货授权，复用仓储库存及统一权限'),
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000004'),'TENANT','warehousing.outbound.coil:plan','warehousing.outbound.coil','plan','设置车次配额','Plan trip quota','设置车次配额，复用仓储库存及统一权限'),
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000005'),'TENANT','warehousing.outbound.coil:pre_reserve','warehousing.outbound.coil','pre_reserve','预留授权额度','Reserve authorization quota','预留授权额度，复用仓储库存及统一权限'),
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000006'),'TENANT','warehousing.outbound.coil:allocate','warehousing.outbound.coil','allocate','整卷配货','Allocate a whole coil','整卷配货，复用仓储库存及统一权限'),
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000007'),'TENANT','warehousing.outbound.coil:cancel','warehousing.outbound.coil','cancel','撤销整卷配货','Cancel coil allocation','撤销整卷配货，复用仓储库存及统一权限'),
 (UUID_TO_BIN('019a65d0-0139-0000-8000-000000000008'),'TENANT','warehousing.outbound.coil:close','warehousing.outbound.coil','close','关闭提货授权','Close pickup authorization','完成撤配后回收授权承诺');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL',resource FROM access_permission WHERE resource='warehousing.outbound.coil' AND scope_type='TENANT';
