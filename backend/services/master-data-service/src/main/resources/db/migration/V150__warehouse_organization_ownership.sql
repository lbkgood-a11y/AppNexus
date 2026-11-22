-- 仓库归属独立于物流组织文本和创建账号；已有归属未知的档案保留 NULL。
ALTER TABLE md_warehouse
 ADD COLUMN organization_id BINARY(16) NULL COMMENT '本租户权威归属组织UUID；未知归属为NULL，组织范围不匹配空值；按仓库记录或全部授权仍可访问',
 ADD INDEX ix_warehouse_organization(tenant_id,organization_id,id);
