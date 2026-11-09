-- 关系表使用复合外键在数据库层约束角色与权限管理域兼容，避免仅依赖应用校验。
ALTER TABLE access_role ADD CONSTRAINT uk_access_role_id_scope UNIQUE(id,scope_type);
ALTER TABLE access_role ADD CONSTRAINT uk_access_role_id_tenant UNIQUE(id,tenant_id);
ALTER TABLE access_permission ADD CONSTRAINT uk_access_permission_id_scope UNIQUE(id,scope_type);
ALTER TABLE tenant_member ADD CONSTRAINT uk_tenant_member_id_tenant UNIQUE(id,tenant_id);

-- 角色只关联独立权限资源；不存在 role_menu 并行授权权威。
CREATE TABLE access_role_permission (
 role_id BINARY(16) NOT NULL,
 permission_id BINARY(16) NOT NULL,
 scope_type VARCHAR(16) NOT NULL,
 granted_by BINARY(16) NOT NULL,
 granted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(role_id,permission_id),
 CONSTRAINT fk_role_permission_role FOREIGN KEY(role_id,scope_type) REFERENCES access_role(id,scope_type),
 CONSTRAINT fk_role_permission_permission FOREIGN KEY(permission_id,scope_type) REFERENCES access_permission(id,scope_type),
 CONSTRAINT ck_role_permission_scope CHECK(scope_type IN('PLATFORM','TENANT')),
 INDEX ix_role_permission_permission(permission_id,role_id)
);

-- 平台账号只能绑定平台角色；账号停用或角色停用后关系保留但不产生有效权限。
CREATE TABLE access_account_role (
 account_id BINARY(16) NOT NULL,
 role_id BINARY(16) NOT NULL,
 scope_type VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
 assigned_by BINARY(16) NOT NULL,
 assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(account_id,role_id),
 CONSTRAINT fk_account_role_account FOREIGN KEY(account_id) REFERENCES identity_account(id),
 CONSTRAINT fk_account_role_role FOREIGN KEY(role_id,scope_type) REFERENCES access_role(id,scope_type),
 CONSTRAINT ck_account_role_scope CHECK(scope_type='PLATFORM'),
 INDEX ix_account_role_role(role_id,account_id)
);

-- 租户成员只能绑定同租户角色；复合外键同时阻止成员和角色跨租户组合。
CREATE TABLE access_member_role (
 tenant_id BINARY(16) NOT NULL,
 member_id BINARY(16) NOT NULL,
 role_id BINARY(16) NOT NULL,
 assigned_by BINARY(16) NOT NULL,
 assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,member_id,role_id),
 CONSTRAINT fk_member_role_member FOREIGN KEY(member_id,tenant_id) REFERENCES tenant_member(id,tenant_id),
 CONSTRAINT fk_member_role_role FOREIGN KEY(role_id,tenant_id) REFERENCES access_role(id,tenant_id),
 INDEX ix_member_role_role(tenant_id,role_id,member_id)
);

-- 授权审计与业务写入同事务提交；快照只保存权限 ID，不记录凭据或个人资料。
CREATE TABLE access_authorization_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 scope_type VARCHAR(16) NOT NULL,
 tenant_id BINARY(16) NULL,
 actor_account_id BINARY(16) NOT NULL,
 action VARCHAR(40) NOT NULL,
 target_type VARCHAR(32) NOT NULL,
 target_id BINARY(16) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 before_snapshot JSON NOT NULL,
 after_snapshot JSON NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_authorization_audit_scope CHECK(
   (scope_type='PLATFORM' AND tenant_id IS NULL) OR (scope_type='TENANT' AND tenant_id IS NOT NULL)
 ),
 CONSTRAINT ck_authorization_audit_action CHECK(action IN('ROLE_PERMISSIONS_REPLACED','ACCOUNT_ROLES_REPLACED','MEMBER_ROLES_REPLACED')),
 INDEX ix_authorization_audit_target(target_type,target_id,occurred_at),
 INDEX ix_authorization_audit_tenant(tenant_id,occurred_at)
);
