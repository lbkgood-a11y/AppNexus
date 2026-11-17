-- 客商档案扩展：历史数据保持空值语义；不修改旧迁移校验和。
ALTER TABLE md_partner
  MODIFY COLUMN partner_type VARCHAR(500) NOT NULL DEFAULT '' COMMENT '客商多角色，逗号分隔固定角色编码；必填，无业务默认值；历史 BOTH 表示客户兼供应商',
  ADD COLUMN registered_place VARCHAR(500) NOT NULL DEFAULT '' COMMENT '注册地',
  ADD COLUMN country_region VARCHAR(500) NOT NULL DEFAULT '' COMMENT '国别地区',
  ADD COLUMN nature VARCHAR(500) NOT NULL DEFAULT '' COMMENT '客商性质',
  ADD COLUMN listed VARCHAR(500) NOT NULL DEFAULT '' COMMENT '是否上市，true/false；空表示未记录',
  ADD COLUMN chinese_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '中文名称',
  ADD COLUMN english_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '英文名称',
  ADD COLUMN short_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '客商简称',
  ADD COLUMN customer_level VARCHAR(500) NOT NULL DEFAULT '' COMMENT '客户等级',
  ADD COLUMN legal_representative VARCHAR(500) NOT NULL DEFAULT '' COMMENT '法人代表',
  ADD COLUMN legal_id VARCHAR(500) NOT NULL DEFAULT '' COMMENT '法人代表身份证',
  ADD COLUMN controller VARCHAR(500) NOT NULL DEFAULT '' COMMENT '实际控制人',
  ADD COLUMN established_on VARCHAR(500) NOT NULL DEFAULT '' COMMENT '成立日期，YYYY-MM-DD；空表示未知',
  ADD COLUMN registered_currency VARCHAR(500) NOT NULL DEFAULT '' COMMENT '注册币种，ISO 4217 三字母代码',
  ADD COLUMN registered_capital VARCHAR(500) NOT NULL DEFAULT '' COMMENT '注册资本，单位万元，非负，最多六位小数；空表示未知',
  ADD COLUMN tax_number VARCHAR(500) NOT NULL DEFAULT '' COMMENT '税则号',
  ADD COLUMN organization_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT '组织机构代码证号',
  ADD COLUMN products_services VARCHAR(500) NOT NULL DEFAULT '' COMMENT '产品或服务',
  ADD COLUMN staff_size VARCHAR(500) NOT NULL DEFAULT '' COMMENT '客户人员规模',
  ADD COLUMN fax VARCHAR(500) NOT NULL DEFAULT '' COMMENT '传真',
  ADD COLUMN remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '备注';

-- 明细与父档案同事务保存，创建审计不可覆盖；所有查询同时限定租户和父客商。
CREATE TABLE md_partner_contact (
  id BINARY(16) NOT NULL COMMENT '明细 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，客户端不可指定',
  partner_id BINARY(16) NOT NULL COMMENT '所属客商 UUID，跟随父对象权限及版本',
  contact_type VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系人类型',
  contact_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系人名称',
  phone VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系人电话',
  address VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系人地址',
  is_default VARCHAR(500) NOT NULL DEFAULT '' COMMENT '默认联系人，true/false；同客商最多一条 true',
  email VARCHAR(500) NOT NULL DEFAULT '' COMMENT '联系人邮箱',
  remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '备注',
  created_by BINARY(16) NULL COMMENT '实际创建账号，可信认证上下文；系统执行可空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时间，微秒精度',
  updated_by BINARY(16) NULL COMMENT '实际最后修改账号；与业务更新同事务',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 修改时间，微秒精度',
  deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '明细逻辑删除标记，默认 false；删除保留历史及最后修改审计',
  PRIMARY KEY(id),
  KEY idx_parent (tenant_id,partner_id)
) ENGINE=InnoDB COMMENT='客商联系人，租户隔离的聚合明细';

-- 明细与父档案同事务保存，创建审计不可覆盖；所有查询同时限定租户和父客商。
CREATE TABLE md_partner_bank_account (
  id BINARY(16) NOT NULL COMMENT '明细 UUID，服务端生成',
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，客户端不可指定',
  partner_id BINARY(16) NOT NULL COMMENT '所属客商 UUID，跟随父对象权限及版本',
  tax_account VARCHAR(500) NOT NULL DEFAULT '' COMMENT '税务专用账号，true/false',
  basic_account VARCHAR(500) NOT NULL DEFAULT '' COMMENT '基本户，true/false；同客商最多一条 true',
  is_default VARCHAR(500) NOT NULL DEFAULT '' COMMENT '默认账号，true/false；同客商最多一条 true',
  bank_name VARCHAR(500) NOT NULL DEFAULT '' COMMENT '银行名称',
  account_number VARCHAR(500) NOT NULL DEFAULT '' COMMENT '银行账号，按文本保存保留前导零',
  account_alias VARCHAR(500) NOT NULL DEFAULT '' COMMENT '账号别名',
  currency VARCHAR(500) NOT NULL DEFAULT '' COMMENT '币种，ISO 4217 三字母大写代码',
  cnaps VARCHAR(500) NOT NULL DEFAULT '' COMMENT '银行 CNAPS 号，空或十二位数字',
  swift_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'SWIFT Code，空或八/十一位标准代码',
  remarks VARCHAR(500) NOT NULL DEFAULT '' COMMENT '备注',
  created_by BINARY(16) NULL COMMENT '实际创建账号，可信认证上下文；系统执行可空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 创建时间，微秒精度',
  updated_by BINARY(16) NULL COMMENT '实际最后修改账号；与业务更新同事务',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成 UTC 修改时间，微秒精度',
  deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '明细逻辑删除标记，默认 false；删除保留历史及最后修改审计',
  PRIMARY KEY(id),
  KEY idx_parent (tenant_id,partner_id)
) ENGINE=InnoDB COMMENT='客商银行账户，租户隔离的聚合明细';
