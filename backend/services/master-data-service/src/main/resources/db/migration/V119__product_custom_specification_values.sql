-- 自定义规格复用固定SPEC定义与结构化值；保留既有标准规格和单选列，不回写历史值。
ALTER TABLE md_product_spec_value
 ADD COLUMN text_value VARCHAR(2000) NULL COMMENT 'TEXT标量；其他类型必须为空',
 ADD COLUMN date_value DATE NULL COMMENT 'DATE自然日；其他类型必须为空',
 ADD UNIQUE KEY uk_spec_value_choice_owner(tenant_id,id,attribute_revision_id,value_type),
 DROP CHECK ck_spec_value_type,
 ADD CONSTRAINT ck_spec_value_types_extended CHECK(
 (value_type='TEXT' AND text_value IS NOT NULL AND date_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL AND option_id IS NULL)
 OR (value_type='DATE' AND text_value IS NULL AND date_value IS NOT NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL AND option_id IS NULL)
 OR (value_type='INTEGER' AND text_value IS NULL AND date_value IS NULL AND integer_value IS NOT NULL AND decimal_value IS NULL AND boolean_value IS NULL AND option_id IS NULL)
 OR (value_type='DECIMAL' AND text_value IS NULL AND date_value IS NULL AND integer_value IS NULL AND decimal_value IS NOT NULL AND boolean_value IS NULL AND option_id IS NULL)
 OR (value_type='BOOLEAN' AND text_value IS NULL AND date_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NOT NULL AND option_id IS NULL)
 OR (value_type IN ('SINGLE_SELECT','MULTI_SELECT') AND text_value IS NULL AND date_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL));
CREATE TABLE md_product_spec_choice (
 id BINARY(16) NOT NULL COMMENT '服务端生成规格枚举值UUID',
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，复合外键隔离租户',
 value_id BINARY(16) NOT NULL COMMENT '所属SPEC结构化值UUID',
 attribute_revision_id BINARY(16) NOT NULL COMMENT '固定属性修订UUID，与父值及选项一致',
 value_type VARCHAR(32) NOT NULL COMMENT 'SINGLE_SELECT或MULTI_SELECT，与父值一致',
 option_id BINARY(16) NULL COMMENT '固定修订下标准选项UUID；自定义时为空',
 custom_text VARCHAR(2000) NULL COMMENT '模板allowCustom允许时的自定义值，与option互斥',
 choice_key BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(CASE WHEN option_id IS NOT NULL THEN CONCAT('option:',HEX(option_id)) ELSE CONCAT('custom:',custom_text) END,256))) STORED COMMENT '标准或自定义值规范摘要，防止同一父值重复',
 single_owner BINARY(16) GENERATED ALWAYS AS (CASE WHEN value_type='SINGLE_SELECT' THEN value_id ELSE NULL END) STORED COMMENT '单选最多一条枚举明细，多选不占此唯一空间',
 created_by BINARY(16) NULL COMMENT '可信创建账号UUID；无账号系统执行为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC创建时间，微秒精度',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号UUID；无账号系统执行为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成UTC修改时间，与正文同事务',
 PRIMARY KEY(id),UNIQUE KEY uk_spec_choice(tenant_id,value_id,choice_key),UNIQUE KEY uk_spec_single(tenant_id,single_owner),
 CONSTRAINT fk_spec_choice_value FOREIGN KEY(tenant_id,value_id,attribute_revision_id,value_type) REFERENCES md_product_spec_value(tenant_id,id,attribute_revision_id,value_type),
 CONSTRAINT fk_spec_choice_option FOREIGN KEY(tenant_id,attribute_revision_id,option_id) REFERENCES md_product_attribute_option(tenant_id,revision_id,id),
 CONSTRAINT ck_spec_choice_enum CHECK(value_type IN ('SINGLE_SELECT','MULTI_SELECT')),
 CONSTRAINT ck_spec_choice_content CHECK((option_id IS NOT NULL AND custom_text IS NULL) OR (option_id IS NULL AND custom_text IS NOT NULL AND CHAR_LENGTH(custom_text)>0))
) COMMENT='自定义规格枚举值，标准选项与自定义文本分离；不创建新的属性定义';
