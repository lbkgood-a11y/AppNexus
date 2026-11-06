-- 授权按租户成员与组织操作显式登记；此迁移不自动授予任何账号权限。
CREATE TABLE organization_capability_grant (
 tenant_id BINARY(16) NOT NULL,
 member_id BINARY(16) NOT NULL,
 capability VARCHAR(20) NOT NULL,
 granted_by BINARY(16) NOT NULL,
 granted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 expires_at TIMESTAMP(6) NULL,
 revoked_at TIMESTAMP(6) NULL,
 PRIMARY KEY (tenant_id,member_id,capability),
 INDEX ix_org_grant_member (tenant_id,member_id,revoked_at,expires_at),
 CONSTRAINT ck_org_capability CHECK (capability IN ('READ','CREATE','UPDATE','DELETE'))
);
