-- 平台级租户目录，租户自身状态决定是否允许访问。
CREATE TABLE platform_tenant (
 id BINARY(16) NOT NULL PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
-- 租户成员表；同账号同租户唯一，后续移除/重新加入复用成员 ID。
CREATE TABLE tenant_member (
 id BINARY(16) NOT NULL PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 account_id BINARY(16) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_member_account (tenant_id,account_id),
 INDEX ix_member_account (account_id,enabled)
);
