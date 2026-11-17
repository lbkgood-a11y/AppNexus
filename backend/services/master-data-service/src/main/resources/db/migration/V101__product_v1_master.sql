-- V1商品主档；仅NONE规格模式，动态主档属性值在独立表保存。
CREATE TABLE md_product_v1 (
 id BINARY(16) NOT NULL COMMENT '商品主档UUID；不重建旧md_product身份',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 code VARCHAR(64) NOT NULL COMMENT '租户内唯一商品编码，创建后不改',
 name VARCHAR(200) NOT NULL COMMENT '商品名称，草稿创建必填',
 short_name VARCHAR(100) NULL COMMENT '商品简称，可为空',
 category_id BINARY(16) NULL COMMENT '商品分类UUID；草稿可为空',
 category_config_id BINARY(16) NULL COMMENT '固定分类配置版本；草稿可为空',
 product_form VARCHAR(16) NULL COMMENT 'PHYSICAL/SERVICE；草稿可为空',
 specification_mode VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'V1固定NONE',
 base_unit_id BINARY(16) NULL COMMENT '实物基础单位必填，服务按配置可空',
 description TEXT NULL COMMENT '商品描述，可为空',
 brand VARCHAR(200) NULL COMMENT '品牌固定字段，不存动态副本',
 origin_text VARCHAR(200) NULL COMMENT '产地，可为空',
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ENABLED/DISABLED',
 structure_state VARCHAR(24) NOT NULL DEFAULT 'INCOMPLETE' COMMENT 'LEGACY_PENDING/INCOMPLETE/COMPLETE',
 draft_archived_at TIMESTAMP(6) NULL COMMENT '草稿归档UTC时间；未归档为空',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '商品聚合CAS版本',
 structure_revision BIGINT NOT NULL DEFAULT 0 COMMENT '结构迁移版本；V1首次创建为0',
 legacy_enabled_snapshot BOOLEAN NULL COMMENT '旧商品enabled历史快照；新商品为空',
 legacy_specification_original TEXT NULL COMMENT '旧规格原文只读；新商品为空',
 legacy_specification_disposition VARCHAR(24) NULL COMMENT 'UNREVIEWED/CONFIRMED_NONE/DEFERRED_SPEC',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pv1_id(tenant_id,id),UNIQUE KEY uk_pv1_code(tenant_id,code),
 CONSTRAINT fk_pv1_category FOREIGN KEY(tenant_id,category_id) REFERENCES md_product_category(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_pv1_config FOREIGN KEY(tenant_id,category_config_id) REFERENCES md_product_category_config(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_pv1_unit FOREIGN KEY(tenant_id,base_unit_id) REFERENCES md_product_unit(tenant_id,id) ON DELETE RESTRICT,
 CHECK(specification_mode='NONE'),CHECK(status IN ('DRAFT','ENABLED','DISABLED')),CHECK(structure_state IN ('LEGACY_PENDING','INCOMPLETE','COMPLETE')),CHECK(version>=0),CHECK(structure_revision>=0)
) COMMENT='V1商品新主档；不承载规格SKU或下游业务';

CREATE TABLE md_product_v1_attribute_value (
 id BINARY(16) NOT NULL COMMENT '商品动态属性值UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 product_id BINARY(16) NOT NULL COMMENT 'V1商品主档UUID',
 template_item_id BINARY(16) NOT NULL COMMENT '固定模板项UUID',
 attribute_revision_id BINARY(16) NOT NULL COMMENT '固定属性修订UUID',
 value_type VARCHAR(32) NOT NULL COMMENT 'TEXT/INTEGER/DECIMAL/BOOLEAN/DATE/SINGLE_SELECT/MULTI_SELECT',
 text_value VARCHAR(2000) NULL COMMENT '文本值；其他标量类型为空',
 integer_value BIGINT NULL COMMENT '整数值',
 decimal_value DECIMAL(30,12) NULL COMMENT '精确小数值',
 boolean_value BOOLEAN NULL COMMENT '布尔值，false合法',
 date_value DATE NULL COMMENT '自然日值',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pv1av_item(tenant_id,product_id,template_item_id),
 CONSTRAINT fk_pv1av_product FOREIGN KEY(tenant_id,product_id) REFERENCES md_product_v1(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_pv1av_item FOREIGN KEY(tenant_id,template_item_id) REFERENCES md_product_template_item(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_pv1av_revision FOREIGN KEY(tenant_id,attribute_revision_id) REFERENCES md_product_attribute_revision(tenant_id,id) ON DELETE RESTRICT,
 CHECK(value_type IN ('TEXT','INTEGER','DECIMAL','BOOLEAN','DATE','SINGLE_SELECT','MULTI_SELECT')),
 CHECK((value_type='TEXT' AND text_value IS NOT NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL AND date_value IS NULL) OR
       (value_type='INTEGER' AND text_value IS NULL AND integer_value IS NOT NULL AND decimal_value IS NULL AND boolean_value IS NULL AND date_value IS NULL) OR
       (value_type='DECIMAL' AND text_value IS NULL AND integer_value IS NULL AND decimal_value IS NOT NULL AND boolean_value IS NULL AND date_value IS NULL) OR
       (value_type='BOOLEAN' AND text_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NOT NULL AND date_value IS NULL) OR
       (value_type='DATE' AND text_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL AND date_value IS NOT NULL) OR
       (value_type IN ('SINGLE_SELECT','MULTI_SELECT') AND text_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL AND date_value IS NULL))
) COMMENT='V1商品主档动态属性值；属性修订与模板项固定';
