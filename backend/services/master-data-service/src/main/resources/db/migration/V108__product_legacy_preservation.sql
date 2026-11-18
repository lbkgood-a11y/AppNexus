-- 存量导入必须逐列保留原审计NULL，不能用迁移时刻伪造历史。
ALTER TABLE md_product_v1
 MODIFY created_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间；存量未知保持NULL，新记录默认数据库时间',
 MODIFY updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间；存量未知保持NULL，新写入数据库时间',
 ADD COLUMN barcode VARCHAR(500) NOT NULL DEFAULT '' COMMENT '商品条码原文，保留前导零，不自动转SKU编码',
 ADD COLUMN legacy_unit_original VARCHAR(500) NULL COMMENT '旧基础单位原文，未确认维度前不自动映射单位',
 ADD COLUMN legacy_source_version BIGINT NULL COMMENT '导入时旧商品CAS版本；切换前必须再次对账，禁止继续双写',
 ADD COLUMN legacy_name_original VARCHAR(200) NULL COMMENT '旧商品名称快照，仅用于源对账，不随新主档修改',
 ADD COLUMN legacy_updated_by_original BINARY(16) NULL COMMENT '旧商品最后修改账号快照，未知保留NULL',
 ADD COLUMN legacy_updated_at_original TIMESTAMP(6) NULL COMMENT '旧商品UTC最后修改时间快照，未知保留NULL';
ALTER TABLE md_product_v1
 ADD CONSTRAINT ck_pv1_form CHECK(product_form IS NULL OR product_form IN ('PHYSICAL','SERVICE')),
 ADD CONSTRAINT ck_pv1_legacy_disposition CHECK(legacy_specification_disposition IS NULL OR legacy_specification_disposition IN ('UNREVIEWED','CONFIRMED_NONE','DEFERRED_SPEC'));
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
 VALUES(UUID_TO_BIN('019a65d0-0108-0000-8000-000000000001'),'TENANT','masterdata.product:stage_legacy','masterdata.product','stage_legacy','暂存旧商品并对账','Stage and reconcile legacy products','受控导入当前租户旧商品并保留ID及原文；不切换正式入口，不授予结构确认或启用。');
