-- 编码能力归属业务对象；所有标识由可信服务端生成，时间按数据库 UTC 会话生成。
CREATE TABLE business_object_encoder (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，必填，不接受客户端覆盖',
 object_type VARCHAR(100) NOT NULL COMMENT '代码声明的业务对象类型，必填',
 encoder_key VARCHAR(40) NOT NULL COMMENT '对象内编码器键，1至40位字母数字及下划线，必填',
 field_key VARCHAR(64) NOT NULL COMMENT '已声明的业务编码字段，必填',
 name VARCHAR(100) NOT NULL COMMENT '编码器名称，1至100字符，必填',
 rule_json JSON NOT NULL COMMENT '有序分段规则，最多10段；发号后不可修改',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否允许新发号，默认启用；停用后拒绝请求重放',
 revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，默认0，每次配置修改加1',
 created_by BINARY(16) NULL COMMENT '可信实际创建账号 UUID；系统执行允许为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID；系统执行允许为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 修改时间，微秒，与业务同事务',
 PRIMARY KEY(tenant_id,object_type,encoder_key)
) COMMENT='租户业务对象编码器配置';
CREATE TABLE business_object_code_counter (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，必填',
 object_type VARCHAR(100) NOT NULL COMMENT '所属业务对象类型，必填',
 encoder_key VARCHAR(40) NOT NULL COMMENT '所属编码器键，必填',
 period_key VARCHAR(8) NOT NULL COMMENT '重置周期键，日yyyyMMdd、月yyyyMM、年yyyy或永久ALL',
 sequence_value BIGINT NOT NULL DEFAULT 0 COMMENT '已分配流水号，默认0，首次发号为1，溢出拒绝，不循环',
 created_by BINARY(16) NULL COMMENT '可信实际创建账号 UUID；系统执行允许为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID；系统执行允许为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 修改时间，微秒，与发号同事务',
 PRIMARY KEY(tenant_id,object_type,encoder_key,period_key)
) COMMENT='编码流水号；事务行锁保证跨进程并发唯一';
CREATE TABLE business_object_code_issue (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，必填',
 actor_id BINARY(16) NOT NULL COMMENT '发号实际账号 UUID，来自可信会话',
 request_key BINARY(16) NOT NULL COMMENT '客户端本次请求 UUID，仅用于幂等，不作为身份凭证',
 object_type VARCHAR(100) NOT NULL COMMENT '所属业务对象类型，必填',
 encoder_key VARCHAR(40) NOT NULL COMMENT '发号编码器键，必填',
 field_key VARCHAR(64) NOT NULL COMMENT '业务编码字段键，必填',
 code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '已发放编码，同租户同对象同字段忽略大小写唯一；弃用不回收',
 created_by BINARY(16) NULL COMMENT '可信实际创建账号 UUID；系统执行允许为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间，微秒',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID；系统执行允许为空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 修改时间，微秒，与发号同事务',
 PRIMARY KEY(tenant_id,actor_id,request_key),
 UNIQUE KEY uk_business_object_issued_code(tenant_id,object_type,field_key,code)
) COMMENT='编码发放和幂等记录；不代表业务单据已经保存';
