-- V1属性模板及不可变版本；V1仅允许BASIC/QUALITY/EXTENSION，SPEC版本暂不允许发布。
CREATE TABLE md_product_template (
 id BINARY(16) NOT NULL COMMENT '模板UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 code VARCHAR(64) NOT NULL COMMENT '租户内模板编码，创建后不改',
 name VARCHAR(200) NOT NULL COMMENT '模板名称',
 purpose VARCHAR(16) NOT NULL COMMENT 'BASIC/QUALITY/EXTENSION；V1不发布SPEC',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '默认启用',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '草稿编辑CAS版本',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pt_id(tenant_id,id),UNIQUE KEY uk_pt_code(tenant_id,code),
 CHECK(purpose IN ('BASIC','QUALITY','EXTENSION')),CHECK(version>=0)
) COMMENT='商品属性模板定义';

CREATE TABLE md_product_template_version (
 id BINARY(16) NOT NULL COMMENT '模板版本UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 template_id BINARY(16) NOT NULL COMMENT '模板UUID',
 version_no BIGINT NOT NULL COMMENT '同模板版本号，从1递增',
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
 description VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '版本说明',
 published_at TIMESTAMP(6) NULL COMMENT '发布UTC时间；草稿为空',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_ptv_id(tenant_id,id),UNIQUE KEY uk_ptv_version(tenant_id,template_id,version_no),
 CONSTRAINT fk_ptv_template FOREIGN KEY(tenant_id,template_id) REFERENCES md_product_template(tenant_id,id) ON DELETE RESTRICT,
 CHECK(version_no>=1),CHECK(state IN ('DRAFT','PUBLISHED','RETIRED'))
) COMMENT='商品属性模板不可变版本';

CREATE TABLE md_product_template_item (
 id BINARY(16) NOT NULL COMMENT '模板项UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 template_version_id BINARY(16) NOT NULL COMMENT '模板版本UUID',
 attribute_id BINARY(16) NOT NULL COMMENT '稳定属性UUID',
 attribute_revision_id BINARY(16) NOT NULL COMMENT '固定属性修订UUID',
 group_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '发布时分组编码快照',
 group_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '发布时分组名称快照',
 required_flag BOOLEAN NOT NULL DEFAULT FALSE COMMENT '启用时是否必填',
 searchable BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许V1固定类型筛选',
 allow_custom BOOLEAN NOT NULL DEFAULT FALSE COMMENT '枚举是否允许显式自定义值',
 sort_order INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pti_id(tenant_id,id),UNIQUE KEY uk_pti_attribute(tenant_id,template_version_id,attribute_id),
 CONSTRAINT fk_pti_version FOREIGN KEY(tenant_id,template_version_id) REFERENCES md_product_template_version(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_pti_attribute FOREIGN KEY(tenant_id,attribute_id) REFERENCES md_product_attribute(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_pti_revision FOREIGN KEY(tenant_id,attribute_revision_id) REFERENCES md_product_attribute_revision(tenant_id,id) ON DELETE RESTRICT
) COMMENT='商品属性模板发布项；正文发布后不可修改';


INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0099-0000-8000-000000000001'),'TENANT','masterdata.producttemplate:read','masterdata.producttemplate','read','查询属性模板','Read product templates','仅当前租户模板读取。'),
(UUID_TO_BIN('019a65d0-0099-0000-8000-000000000002'),'TENANT','masterdata.producttemplate:create','masterdata.producttemplate','create','新增属性模板','Create product templates','仅当前租户模板创建。'),
(UUID_TO_BIN('019a65d0-0099-0000-8000-000000000003'),'TENANT','masterdata.producttemplate:update','masterdata.producttemplate','update','编辑属性模板','Update product templates','仅草稿版本维护。'),
(UUID_TO_BIN('019a65d0-0099-0000-8000-000000000004'),'TENANT','masterdata.producttemplate:publish','masterdata.producttemplate','publish','发布属性模板','Publish product templates','发布后版本正文不可变。');
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
SELECT r.id,p.id,'TENANT',COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000)
FROM access_role r JOIN access_permission p ON p.code='masterdata.producttemplate:read' WHERE r.system_key LIKE 'TENANT_ADMIN:%';
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type)
VALUES(UUID_TO_BIN('019a65d0-0099-0000-8000-000000000101'),UUID_TO_BIN('019a65d0-0075-7000-8000-000000000100'),'MENU','builtin.navigation.masterdata.product-template','属性模板','Product templates','/master-data/product-template','master-data/product-template','ri-layout-grid-line','masterdata.producttemplate:read',9,'TENANT');
INSERT INTO platform_menu_resource(menu_id,resource_code) VALUES(UUID_TO_BIN('019a65d0-0099-0000-8000-000000000101'),'masterdata.producttemplate');
