-- V1属性定义、不可变修订及枚举选项；模板发布在后续V99接入。
CREATE TABLE md_product_attribute_group (
 id BINARY(16) NOT NULL COMMENT '属性分组UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 code VARCHAR(64) NOT NULL COMMENT '租户内唯一分组编码，创建后不改',
 name VARCHAR(200) NOT NULL COMMENT '分组名称，必填',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pag_id(tenant_id,id),UNIQUE KEY uk_pag_code(tenant_id,code),CHECK(version>=0)
) COMMENT='商品属性分组';

CREATE TABLE md_product_attribute (
 id BINARY(16) NOT NULL COMMENT '稳定属性UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 code VARCHAR(64) NOT NULL COMMENT '稳定属性编码，创建后不可改',
 group_id BINARY(16) NULL COMMENT '同租户属性分组，可为空',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pa_id(tenant_id,id),UNIQUE KEY uk_pa_code(tenant_id,code),
 CONSTRAINT fk_pa_group FOREIGN KEY(tenant_id,group_id) REFERENCES md_product_attribute_group(tenant_id,id) ON DELETE RESTRICT,
 CHECK(version>=0)
) COMMENT='商品稳定属性定义';

CREATE TABLE md_product_attribute_revision (
 id BINARY(16) NOT NULL COMMENT '属性修订UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 attribute_id BINARY(16) NOT NULL COMMENT '稳定属性UUID',
 revision_no BIGINT NOT NULL COMMENT '同属性修订号，从1递增',
 name VARCHAR(200) NOT NULL COMMENT '发布时属性名称快照',
 data_type VARCHAR(32) NOT NULL COMMENT 'TEXT/INTEGER/DECIMAL/BOOLEAN/SINGLE_SELECT/MULTI_SELECT/DATE',
 unit_id BINARY(16) NULL COMMENT '数值属性单位UUID；非数值必须为空',
 numeric_scale INT NULL COMMENT '数值小数位0至12；非数值为空',
 validation_json JSON NULL COMMENT '有限声明式校验规则；不允许SQL或脚本',
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
 published_at TIMESTAMP(6) NULL COMMENT '发布UTC时间；草稿为空',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_par_id(tenant_id,id),UNIQUE KEY uk_par_revision(tenant_id,attribute_id,revision_no),
 CONSTRAINT fk_par_attribute FOREIGN KEY(tenant_id,attribute_id) REFERENCES md_product_attribute(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_par_unit FOREIGN KEY(tenant_id,unit_id) REFERENCES md_product_unit(tenant_id,id) ON DELETE RESTRICT,
 CHECK(revision_no>=1),CHECK(numeric_scale IS NULL OR numeric_scale BETWEEN 0 AND 12),
 CHECK(data_type IN ('TEXT','INTEGER','DECIMAL','BOOLEAN','SINGLE_SELECT','MULTI_SELECT','DATE')),
 CHECK(state IN ('DRAFT','PUBLISHED','RETIRED'))
) COMMENT='商品属性不可变修订';

CREATE TABLE md_product_attribute_option (
 id BINARY(16) NOT NULL COMMENT '枚举选项UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 revision_id BINARY(16) NOT NULL COMMENT '属性修订UUID',
 option_code VARCHAR(64) NOT NULL COMMENT '修订内稳定选项编码',
 label VARCHAR(200) NOT NULL COMMENT '选项显示名称快照',
 sort_order INT NOT NULL DEFAULT 0 COMMENT '显示顺序，默认0',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用，历史选项不物理删除',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pao_id(tenant_id,id),UNIQUE KEY uk_pao_code(tenant_id,revision_id,option_code),
 CONSTRAINT fk_pao_revision FOREIGN KEY(tenant_id,revision_id) REFERENCES md_product_attribute_revision(tenant_id,id) ON DELETE RESTRICT
) COMMENT='商品属性枚举选项';


INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0098-0000-8000-000000000001'),'TENANT','masterdata.productattribute:read','masterdata.productattribute','read','查询商品属性','Read product attributes','仅当前租户属性定义读取。'),
(UUID_TO_BIN('019a65d0-0098-0000-8000-000000000002'),'TENANT','masterdata.productattribute:create','masterdata.productattribute','create','新增商品属性','Create product attributes','仅当前租户属性定义新增。'),
(UUID_TO_BIN('019a65d0-0098-0000-8000-000000000003'),'TENANT','masterdata.productattribute:update','masterdata.productattribute','update','编辑商品属性','Update product attributes','仅草稿修订和值选项维护。'),
(UUID_TO_BIN('019a65d0-0098-0000-8000-000000000004'),'TENANT','masterdata.productattribute:publish','masterdata.productattribute','publish','发布商品属性','Publish product attributes','发布后修订不可原地修改。');
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000)
FROM access_role r JOIN access_permission p ON p.code='masterdata.productattribute:read' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type)
VALUES(UUID_TO_BIN('019a65d0-0098-0000-8000-000000000101'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.product-attribute','属性管理','Product attributes','/master-data/product-attribute','master-data/product-attribute','ri-list-settings-line','masterdata.productattribute:read',8,'TENANT');
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0098-0000-8000-000000000101'),'masterdata.productattribute');
