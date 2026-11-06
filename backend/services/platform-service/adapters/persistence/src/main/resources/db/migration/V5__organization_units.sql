-- 首个正式租户业务表。每个查询和变更都必须由 @TenantScoped Mapper 加 tenant_id。
CREATE TABLE tenant_organization_unit (
 tenant_id BINARY(16) NOT NULL,
 id BINARY(16) NOT NULL,
 name VARCHAR(100) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (tenant_id,id),
 CONSTRAINT uk_org_name_per_tenant UNIQUE (tenant_id,name),
 INDEX ix_org_tenant_created (tenant_id,created_at)
);
