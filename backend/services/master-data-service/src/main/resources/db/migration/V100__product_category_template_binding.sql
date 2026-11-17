-- V1分类配置与模板版本绑定；发布配置时固定四槽，已发布正文不可修改。
CREATE TABLE md_product_category_template (
 id BINARY(16) NOT NULL COMMENT '分类配置模板绑定UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 config_id BINARY(16) NOT NULL COMMENT '不可变分类配置UUID',
 purpose VARCHAR(16) NOT NULL COMMENT 'BASIC/QUALITY/EXTENSION；SPEC槽V1必须为空',
 template_version_id BINARY(16) NOT NULL COMMENT '已发布模板版本UUID',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间',
 updated_by BINARY(16) NULL COMMENT '可信修改账号UUID；系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC修改时间',
 PRIMARY KEY(id),UNIQUE KEY uk_pct_id(tenant_id,id),UNIQUE KEY uk_pct_slot(tenant_id,config_id,purpose),
 CONSTRAINT fk_pct_config FOREIGN KEY(tenant_id,config_id) REFERENCES md_product_category_config(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_pct_version FOREIGN KEY(tenant_id,template_version_id) REFERENCES md_product_template_version(tenant_id,id) ON DELETE RESTRICT,
 CHECK(purpose IN ('BASIC','QUALITY','EXTENSION'))
) COMMENT='商品分类配置到属性模板版本的不可变绑定';
