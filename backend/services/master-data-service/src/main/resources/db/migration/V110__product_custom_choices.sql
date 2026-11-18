-- 自定义枚举值与标准选项互斥，仍归固定修订；不自动新增字典选项。
ALTER TABLE md_product_v1_attribute_choice
 MODIFY option_id BINARY(16) NULL COMMENT '固定修订选项UUID；自定义输入时为空',
 ADD COLUMN custom_text VARCHAR(2000) NULL COMMENT '明确自定义输入原文，最多2000字符，不增加枚举定义',
 ADD COLUMN choice_key BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(CASE WHEN option_id IS NULL THEN CONCAT('custom:',custom_text) ELSE CONCAT('option:',HEX(option_id)) END,256))) STORED COMMENT '父值内去重键，选项UUID或自定义文本SHA256',
 ADD UNIQUE KEY uk_pv1choice_key(tenant_id,value_id,choice_key),
 ADD CONSTRAINT ck_pv1choice_kind CHECK((option_id IS NOT NULL AND custom_text IS NULL) OR (option_id IS NULL AND custom_text IS NOT NULL AND CHAR_LENGTH(TRIM(custom_text))>0));
