-- 补全V1固定配置/模板项/属性修订的一致性约束；不放宽原表审计。
ALTER TABLE md_product_template ADD UNIQUE KEY uk_pt_purpose(tenant_id,id,purpose);
ALTER TABLE md_product_template_version ADD COLUMN purpose VARCHAR(16) NULL COMMENT '模板用途冗余，由父模板派生，复合外键保证一致';
UPDATE md_product_template_version v JOIN md_product_template t ON t.tenant_id=v.tenant_id AND t.id=v.template_id SET v.purpose=t.purpose;
ALTER TABLE md_product_template_version MODIFY purpose VARCHAR(16) NOT NULL COMMENT '模板用途快照，V1仅BASIC/QUALITY/EXTENSION',
 ADD UNIQUE KEY uk_ptv_purpose(tenant_id,id,purpose),
 ADD CONSTRAINT fk_ptv_purpose FOREIGN KEY(tenant_id,template_id,purpose) REFERENCES md_product_template(tenant_id,id,purpose);
ALTER TABLE md_product_attribute_revision ADD UNIQUE KEY uk_par_owner_type(tenant_id,attribute_id,id,data_type),
 ADD UNIQUE KEY uk_par_type(tenant_id,id,data_type),
 ADD CONSTRAINT ck_par_unit CHECK(data_type IN ('INTEGER','DECIMAL') OR (unit_id IS NULL AND numeric_scale IS NULL)),
 ADD CONSTRAINT ck_par_scale CHECK((data_type='INTEGER' AND numeric_scale=0) OR (data_type='DECIMAL' AND numeric_scale BETWEEN 0 AND 12) OR data_type NOT IN ('INTEGER','DECIMAL'));
ALTER TABLE md_product_template_item ADD COLUMN data_type VARCHAR(32) NULL COMMENT '属性数据类型快照，由固定修订派生';
UPDATE md_product_template_item i JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id SET i.data_type=r.data_type;
ALTER TABLE md_product_template_item MODIFY data_type VARCHAR(32) NOT NULL COMMENT '固定属性修订数据类型，禁止客户端任意指定',
 ADD UNIQUE KEY uk_pti_chain(tenant_id,template_version_id,id,attribute_id,attribute_revision_id,data_type),
 ADD CONSTRAINT fk_pti_owner_revision FOREIGN KEY(tenant_id,attribute_id,attribute_revision_id,data_type) REFERENCES md_product_attribute_revision(tenant_id,attribute_id,id,data_type);
ALTER TABLE md_product_category_template
 ADD UNIQUE KEY uk_pct_chain(tenant_id,config_id,purpose,template_version_id),
 ADD CONSTRAINT fk_pct_purpose FOREIGN KEY(tenant_id,template_version_id,purpose) REFERENCES md_product_template_version(tenant_id,id,purpose);

CREATE TABLE md_product_category_config_item (
 id BINARY(16) NOT NULL COMMENT '配置项UUID，发布时服务端完整物化',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 config_id BINARY(16) NOT NULL COMMENT '固定分类配置UUID',
 purpose VARCHAR(16) NOT NULL COMMENT 'BASIC/QUALITY/EXTENSION，均归商品主档',
 template_version_id BINARY(16) NOT NULL COMMENT '已绑定模板版本UUID',
 template_item_id BINARY(16) NOT NULL COMMENT '固定模板项UUID',
 attribute_id BINARY(16) NOT NULL COMMENT '稳定属性UUID，用于跨四槽去重',
 attribute_revision_id BINARY(16) NOT NULL COMMENT '固定属性修订UUID',
 data_type VARCHAR(32) NOT NULL COMMENT '固定属性类型，复合外键约束',
   created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空，禁止客户端指定',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒精度，新建默认当前时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空，禁止客户端指定',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC最后修改时间，微秒精度，与正文同事务更新',
 PRIMARY KEY(id),
 UNIQUE KEY uk_pcci_attribute(tenant_id,config_id,attribute_id),
 UNIQUE KEY uk_pcci_value(tenant_id,config_id,template_item_id,attribute_revision_id,data_type),
 CONSTRAINT fk_pcci_binding FOREIGN KEY(tenant_id,config_id,purpose,template_version_id) REFERENCES md_product_category_template(tenant_id,config_id,purpose,template_version_id),
 CONSTRAINT fk_pcci_item FOREIGN KEY(tenant_id,template_version_id,template_item_id,attribute_id,attribute_revision_id,data_type) REFERENCES md_product_template_item(tenant_id,template_version_id,id,attribute_id,attribute_revision_id,data_type)
) COMMENT='不可变配置项投影，跨模板稳定属性唯一';

-- 已有发布绑定在相同迁移内物化；重复稳定属性会失败，不能静默择一。
INSERT INTO md_product_category_config_item(id,tenant_id,config_id,purpose,template_version_id,template_item_id,attribute_id,attribute_revision_id,data_type,created_by,updated_by)
 SELECT UUID_TO_BIN(UUID()),b.tenant_id,b.config_id,b.purpose,b.template_version_id,i.id,i.attribute_id,i.attribute_revision_id,i.data_type,NULL,NULL
 FROM md_product_category_template b JOIN md_product_template_item i ON i.tenant_id=b.tenant_id AND i.template_version_id=b.template_version_id;

ALTER TABLE md_product_v1 ADD UNIQUE KEY uk_pv1_config(tenant_id,id,category_config_id),
 ADD CONSTRAINT fk_pv1_config_owner FOREIGN KEY(tenant_id,category_id,category_config_id) REFERENCES md_product_category_config(tenant_id,category_id,id),
 ADD CONSTRAINT ck_pv1_config_category CHECK(category_config_id IS NULL OR category_id IS NOT NULL);
ALTER TABLE md_product_v1_attribute_value ADD COLUMN category_config_id BINARY(16) NULL COMMENT '商品固定配置UUID，不接收其他配置模板项';
UPDATE md_product_v1_attribute_value v JOIN md_product_v1 p ON p.tenant_id=v.tenant_id AND p.id=v.product_id SET v.category_config_id=p.category_config_id;
ALTER TABLE md_product_v1_attribute_value MODIFY category_config_id BINARY(16) NOT NULL COMMENT '商品固定配置UUID，复合外键校验',
 ADD UNIQUE KEY uk_pv1av_id_revision(tenant_id,id,attribute_revision_id),
 ADD CONSTRAINT fk_pv1av_config FOREIGN KEY(tenant_id,product_id,category_config_id) REFERENCES md_product_v1(tenant_id,id,category_config_id),
 ADD CONSTRAINT fk_pv1av_chain FOREIGN KEY(tenant_id,category_config_id,template_item_id,attribute_revision_id,value_type) REFERENCES md_product_category_config_item(tenant_id,config_id,template_item_id,attribute_revision_id,data_type);
ALTER TABLE md_product_attribute_option ADD UNIQUE KEY uk_pao_revision_id(tenant_id,revision_id,id);

CREATE TABLE md_product_v1_attribute_choice (
 id BINARY(16) NOT NULL COMMENT '商品枚举选择UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 value_id BINARY(16) NOT NULL COMMENT '父属性值UUID',
 attribute_revision_id BINARY(16) NOT NULL COMMENT '固定属性修订UUID',
 option_id BINARY(16) NOT NULL COMMENT '固定修订下的枚举选项UUID',
   created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空，禁止客户端指定',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒精度，新建默认当前时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空，禁止客户端指定',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC最后修改时间，微秒精度，与正文同事务更新',
 PRIMARY KEY(id),UNIQUE KEY uk_pv1choice(tenant_id,value_id,option_id),
 CONSTRAINT fk_pv1choice_value FOREIGN KEY(tenant_id,value_id,attribute_revision_id) REFERENCES md_product_v1_attribute_value(tenant_id,id,attribute_revision_id),
 CONSTRAINT fk_pv1choice_option FOREIGN KEY(tenant_id,attribute_revision_id,option_id) REFERENCES md_product_attribute_option(tenant_id,revision_id,id)
) COMMENT='商品动态枚举值，不能引用另一属性修订选项';

CREATE TABLE md_product_v1_change (
 id BINARY(16) NOT NULL COMMENT '商品变更事件UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID',
 product_id BINARY(16) NOT NULL COMMENT '商品UUID',
 to_version BIGINT NOT NULL COMMENT '变更后商品CAS版本',
 event_type VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/STATUS/ARCHIVE/RESTORE',
 before_snapshot JSON NULL COMMENT '服务端构造的授权变更前快照，首次创建为空',
 after_snapshot JSON NOT NULL COMMENT '包含固定配置ID、值和四审计的变更后快照',
 schema_version INT NOT NULL DEFAULT 1 COMMENT '只读快照结构版本，默认1',
   created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空，禁止客户端指定',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC创建时间，微秒精度，新建默认当前时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空，禁止客户端指定',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库UTC最后修改时间，微秒精度，与正文同事务更新',
 PRIMARY KEY(id),UNIQUE KEY uk_pv1change_version(tenant_id,product_id,to_version),
 CONSTRAINT fk_pv1change_product FOREIGN KEY(tenant_id,product_id) REFERENCES md_product_v1(tenant_id,id)
) COMMENT='商品追加变更记录，与商品及动态值同事务写入';
