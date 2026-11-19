-- 客商数据归属为业务字段，不使用审计创建人代替；历史未知组织保持 NULL，不猜测组织。
ALTER TABLE md_partner ADD COLUMN organization_id BINARY(16) NULL COMMENT '客商归属组织UUID；历史未知为NULL，仅当前租户全部范围可读取，新增必填可信租户启用组织',
 ADD INDEX ix_partner_organization(tenant_id,organization_id,id);
