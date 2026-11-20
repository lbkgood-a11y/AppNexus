-- 编码规则按租户独立定义，不可变版本由业务对象绑定显式引用；不迁移流水及幂等主键。
CREATE TABLE coding_rule_version (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户UUID，必填，不接受客户端覆盖',
 rule_key VARCHAR(40) NOT NULL COMMENT '租户内规则标识，字母开头，最长40字符',
 rule_version BIGINT NOT NULL COMMENT '不可变版本号，从1递增；绑定不会自动升级',
 name VARCHAR(100) NOT NULL COMMENT '规则版本名称，1至100字符，必填',
 rule_json JSON NOT NULL COMMENT '固定文本、服务端日期及流水分段，最大输出128字符，不可原地更新',
 created_by BINARY(16) NULL COMMENT '可信实际创建账号UUID；历史未知保留NULL',
 created_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的UTC创建时间，微秒；历史未知保留NULL',
 updated_by BINARY(16) NULL COMMENT '可信实际修改账号UUID；不可变版本与创建信息一致，历史未知保留NULL',
 updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的UTC修改时间，微秒；历史未知保留NULL',
 PRIMARY KEY(tenant_id,rule_key,rule_version)
) COMMENT='租户独立编码规则不可变版本';
ALTER TABLE business_object_encoder
 ADD COLUMN rule_key VARCHAR(40) NULL COMMENT '固定引用的当前租户独立规则标识，迁移完成后必填',
 ADD COLUMN rule_version BIGINT NULL COMMENT '固定引用规则版本，正整数，不跟随新版本自动升级';
-- 每个历史编码器独立生成规则标识，完整保留历史审计；不推断重复配置可以合并。
UPDATE business_object_encoder SET rule_key=CONCAT('R',REPLACE(UUID(),'-','')),rule_version=1;
INSERT INTO coding_rule_version(tenant_id,rule_key,rule_version,name,rule_json,created_by,created_at,updated_by,updated_at)
 SELECT tenant_id,rule_key,rule_version,name,rule_json,created_by,created_at,updated_by,updated_at
 FROM business_object_encoder;
ALTER TABLE business_object_encoder
 MODIFY rule_key VARCHAR(40) NOT NULL COMMENT '固定引用的当前租户独立规则标识，必填',
 MODIFY rule_version BIGINT NOT NULL COMMENT '固定引用规则版本，正整数，不自动升级',
 DROP COLUMN rule_json,
 ADD CONSTRAINT fk_encoder_rule_version FOREIGN KEY(tenant_id,rule_key,rule_version)
 REFERENCES coding_rule_version(tenant_id,rule_key,rule_version);
