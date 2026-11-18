-- 扩展规格/SKU/组合商品；身份和历史不物理删除，当前组合唯一性由生成列约束。
ALTER TABLE md_product_category_config_item ADD UNIQUE KEY uk_pcci_spec_scope(tenant_id,config_id,purpose,template_item_id,attribute_revision_id,data_type);
-- 数据库层阻止SPEC值误写商品主档，不能只依赖前端分区或服务端用途判断。
ALTER TABLE md_product_category_config_item
 ADD COLUMN owner_scope VARCHAR(16) GENERATED ALWAYS AS (CASE WHEN purpose='SPEC' THEN 'SPECIFICATION' ELSE 'PRODUCT' END) STORED COMMENT '属性语义归属，SPEC固定规格，其余固定主档',
 ADD UNIQUE KEY uk_pcci_owner_scope(tenant_id,config_id,template_item_id,attribute_revision_id,data_type,owner_scope);
ALTER TABLE md_product_v1_attribute_value
 ADD COLUMN owner_scope VARCHAR(16) GENERATED ALWAYS AS ('PRODUCT') STORED COMMENT '主档值固定PRODUCT，禁止写入SPEC模板项',
 ADD CONSTRAINT fk_pav_owner_scope FOREIGN KEY(tenant_id,category_config_id,template_item_id,attribute_revision_id,value_type,owner_scope) REFERENCES md_product_category_config_item(tenant_id,config_id,template_item_id,attribute_revision_id,data_type,owner_scope);

CREATE TABLE md_product_specification (
 id BINARY(16) NOT NULL COMMENT '服务端生成UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，所有归属复合外键使用',
 product_id BINARY(16) NOT NULL COMMENT '所属商品UUID',
 config_id BINARY(16) NOT NULL COMMENT '规格创建时固定配置版本UUID',
 mode VARCHAR(16) NOT NULL COMMENT 'STANDARD或CUSTOM；有规格不必创建SKU',
 name VARCHAR(200) NOT NULL COMMENT '规格展示名称，必填，最大200字符',
 description VARCHAR(2000) NULL COMMENT '自定义规格补充说明，最大2000字符',
 combination_canonical TEXT NULL COMMENT '标准规格规范串，固定修订及值排序产生，不接收客户端签名',
 combination_hash BINARY(32) NULL COMMENT '标准规范串SHA256，与规范串共同复核碰撞',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '规格独立启停，受商品父状态限制',
 retired_at TIMESTAMP(6) NULL COMMENT '结构退出时间，退出后不可恢复或重新绑定SKU',
 current_hash BINARY(32) GENERATED ALWAYS AS (CASE WHEN retired_at IS NULL AND mode='STANDARD' THEN combination_hash ELSE NULL END) STORED COMMENT '仅当前标准规格占用唯一组合空间，停用仍占用',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '规格CAS，初始0',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统无主体为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；系统无主体为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，与正文同事务',
 PRIMARY KEY(id),UNIQUE KEY uk_spec_owner(tenant_id,product_id,id),UNIQUE KEY uk_spec_id(tenant_id,id),UNIQUE KEY uk_spec_config(tenant_id,id,config_id),
 UNIQUE KEY uk_spec_combination(tenant_id,product_id,current_hash),
 CONSTRAINT fk_spec_product FOREIGN KEY(tenant_id,product_id) REFERENCES md_product_v1(tenant_id,id),
 CONSTRAINT fk_spec_config FOREIGN KEY(tenant_id,config_id) REFERENCES md_product_category_config(tenant_id,id),
 CONSTRAINT ck_spec_mode CHECK(mode IN ('STANDARD','CUSTOM')),
 CONSTRAINT ck_spec_signature CHECK((mode='STANDARD' AND combination_canonical IS NOT NULL AND combination_hash IS NOT NULL) OR (mode='CUSTOM' AND combination_canonical IS NULL AND combination_hash IS NULL)),
 CONSTRAINT ck_spec_version CHECK(version>=0)
) COMMENT='商品规格身份，与可选SKU解耦';
CREATE TABLE md_product_spec_value (
 id BINARY(16) NOT NULL COMMENT '服务端生成UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，所有归属复合外键使用',
 specification_id BINARY(16) NOT NULL COMMENT '所属规格UUID',
 config_id BINARY(16) NOT NULL COMMENT '固定配置版本，由规格派生',
 purpose VARCHAR(16) NOT NULL DEFAULT 'SPEC' COMMENT '固定SPEC归属，复合外键拒绝主档模板项',
 template_item_id BINARY(16) NOT NULL COMMENT 'SPEC模板项UUID',
 attribute_revision_id BINARY(16) NOT NULL COMMENT '固定属性修订UUID',
 value_type VARCHAR(32) NOT NULL COMMENT 'INTEGER/DECIMAL/BOOLEAN/SINGLE_SELECT标准组合类型',
 integer_value BIGINT NULL COMMENT '整数值，仅INTEGER使用',
 decimal_value DECIMAL(30,12) NULL COMMENT '精确小数，仅DECIMAL使用',
 boolean_value BOOLEAN NULL COMMENT '布尔值，仅BOOLEAN使用，false合法',
 option_id BINARY(16) NULL COMMENT '固定修订单选UUID，仅SINGLE_SELECT使用',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统无主体为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；系统无主体为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，与正文同事务',
 PRIMARY KEY(id),UNIQUE KEY uk_spec_value(tenant_id,specification_id,template_item_id),
 CONSTRAINT fk_spec_value_parent FOREIGN KEY(tenant_id,specification_id,config_id) REFERENCES md_product_specification(tenant_id,id,config_id),
 CONSTRAINT fk_spec_value_config FOREIGN KEY(tenant_id,config_id,purpose,template_item_id,attribute_revision_id,value_type) REFERENCES md_product_category_config_item(tenant_id,config_id,purpose,template_item_id,attribute_revision_id,data_type),
 CONSTRAINT fk_spec_option FOREIGN KEY(tenant_id,attribute_revision_id,option_id) REFERENCES md_product_attribute_option(tenant_id,revision_id,id),
 CONSTRAINT ck_spec_value_scope CHECK(purpose='SPEC'),
 CONSTRAINT ck_spec_value_type CHECK((value_type='INTEGER' AND integer_value IS NOT NULL AND decimal_value IS NULL AND boolean_value IS NULL AND option_id IS NULL) OR (value_type='DECIMAL' AND integer_value IS NULL AND decimal_value IS NOT NULL AND boolean_value IS NULL AND option_id IS NULL) OR (value_type='BOOLEAN' AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NOT NULL AND option_id IS NULL) OR (value_type='SINGLE_SELECT' AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL AND option_id IS NOT NULL))
) COMMENT='标准规格结构化属性值，禁止将SPEC值写到主档';
CREATE TABLE md_product_sku (
 id BINARY(16) NOT NULL COMMENT '服务端生成UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，所有归属复合外键使用',
 product_id BINARY(16) NOT NULL COMMENT '所属商品UUID，创建后不可改变',
 specification_id BINARY(16) NOT NULL COMMENT '所属规格UUID，创建后不可改变',
 code VARCHAR(64) NOT NULL COMMENT '租户内唯一SKU编码，创建后不可改、不回收',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '独立启停，有效使用还需商品和规格启用且未退役',
 version BIGINT NOT NULL DEFAULT 0 COMMENT 'SKU启停CAS，初始0',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统无主体为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；系统无主体为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，与正文同事务',
 PRIMARY KEY(id),UNIQUE KEY uk_sku_code(tenant_id,code),
 CONSTRAINT fk_sku_owner FOREIGN KEY(tenant_id,product_id,specification_id) REFERENCES md_product_specification(tenant_id,product_id,id),
 CONSTRAINT ck_sku_version CHECK(version>=0)
) COMMENT='显式可选SKU身份，不为无规格和自定义规格自动生成';
CREATE TABLE md_product_component (
 id BINARY(16) NOT NULL COMMENT '服务端生成UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，所有归属复合外键使用',
 product_id BINARY(16) NOT NULL COMMENT '组合商品UUID',
 component_product_id BINARY(16) NOT NULL COMMENT '同租户组成商品UUID，禁止自引用和间接环',
 quantity DECIMAL(30,12) NOT NULL COMMENT '按组成商品基础单位的正数数量，最多12位小数，不自动换算',
 component_version BIGINT NOT NULL COMMENT '加入组合时组成商品版本快照，不自动刷新',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '组成关系启停，保留历史',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '关系CAS，初始0',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统无主体为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；系统无主体为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，与正文同事务',
 PRIMARY KEY(id),UNIQUE KEY uk_component(tenant_id,product_id,component_product_id),
 CONSTRAINT fk_component_parent FOREIGN KEY(tenant_id,product_id) REFERENCES md_product_v1(tenant_id,id),
 CONSTRAINT fk_component_child FOREIGN KEY(tenant_id,component_product_id) REFERENCES md_product_v1(tenant_id,id),
 CONSTRAINT ck_component_quantity CHECK(quantity>0),
 CONSTRAINT ck_component_self CHECK(product_id<>component_product_id),
 CONSTRAINT ck_component_version CHECK(version>=0 AND component_version>=0)
) COMMENT='组合商品自身组成，不涉及库存拆装或价格分摊';
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0116-0000-8000-000000000001'),'TENANT','masterdata.product:manage_structure','masterdata.product','manage_structure','维护规格SKU及组合','Manage product structure','停用或草稿下维护规格SKU和组合，父CAS及追加历史，禁止改变已有SKU身份');
