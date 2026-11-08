-- 角色同时覆盖平台域与租户域；scope_owner_id 归一化 NULL，保证平台编码与租户内编码分别永久唯一。
CREATE TABLE access_role (
 id BINARY(16) NOT NULL PRIMARY KEY,
 scope_type VARCHAR(16) NOT NULL,
 tenant_id BINARY(16) NULL,
 role_type VARCHAR(16) NOT NULL,
 system_key VARCHAR(64) NULL,
 code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(100) NOT NULL,
 description VARCHAR(500) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 sort_order INT NOT NULL DEFAULT 100,
 version BIGINT NOT NULL DEFAULT 0,
 created_by BINARY(16) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_by BINARY(16) NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted_by BINARY(16) NULL,
 deleted_at TIMESTAMP(6) NULL,
 delete_reason VARCHAR(500) NULL,
 scope_owner_id BINARY(16) GENERATED ALWAYS AS (
   CASE WHEN scope_type='PLATFORM' THEN 0x00000000000000000000000000000000 ELSE tenant_id END
 ) STORED,
 CONSTRAINT uk_access_role_scope_code UNIQUE(scope_owner_id,code),
 CONSTRAINT uk_access_role_system_key UNIQUE(system_key),
 CONSTRAINT ck_access_role_scope CHECK(
   (scope_type='PLATFORM' AND tenant_id IS NULL) OR
   (scope_type='TENANT' AND tenant_id IS NOT NULL AND tenant_id<>0x00000000000000000000000000000000)
 ),
 CONSTRAINT ck_access_role_type CHECK(role_type IN('SYSTEM','CUSTOM')),
 CONSTRAINT ck_access_role_system_key CHECK(
   (role_type='SYSTEM' AND system_key IS NOT NULL) OR
   (role_type='CUSTOM' AND system_key IS NULL)
 ),
 CONSTRAINT ck_access_role_status CHECK(status IN('ENABLED','DISABLED')),
 CONSTRAINT ck_access_role_code CHECK(REGEXP_LIKE(code,'^[a-z][a-z0-9_]{1,63}$','c')),
 CONSTRAINT ck_access_role_reserved_code CHECK(role_type='SYSTEM' OR code NOT LIKE 'sys\\_%'),
 CONSTRAINT ck_access_role_sort CHECK(sort_order BETWEEN 0 AND 9999),
 CONSTRAINT ck_access_role_delete CHECK(
   (deleted_at IS NULL AND deleted_by IS NULL AND delete_reason IS NULL) OR
   (deleted_at IS NOT NULL AND delete_reason IS NOT NULL AND CHAR_LENGTH(TRIM(delete_reason))>0)
 ),
 INDEX ix_access_role_tenant_status(tenant_id,status,sort_order),
 INDEX ix_access_role_scope_status(scope_type,status,sort_order),
 INDEX ix_access_role_scope_name(scope_owner_id,name)
);

-- 角色审计为追加记录；JSON 快照只包含角色配置，不保存账号凭据、会话或 Token。
CREATE TABLE access_role_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 role_id BINARY(16) NOT NULL,
 scope_type VARCHAR(16) NOT NULL,
 tenant_id BINARY(16) NULL,
 actor_account_id BINARY(16) NOT NULL,
 action VARCHAR(32) NOT NULL,
 reason VARCHAR(500) NULL,
 before_snapshot JSON NULL,
 after_snapshot JSON NULL,
 request_id VARCHAR(100) NULL,
 occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_access_role_audit_action CHECK(action IN(
   'ROLE_CREATED','ROLE_UPDATED','ROLE_ENABLED','ROLE_DISABLED','ROLE_DELETED'
 )),
 INDEX ix_access_role_audit_role(role_id,occurred_at),
 INDEX ix_access_role_audit_tenant(tenant_id,occurred_at)
);
