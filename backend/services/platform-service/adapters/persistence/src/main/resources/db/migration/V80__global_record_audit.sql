-- 全局记录审计：新增列不回填历史操作者或历史时间，避免把迁移执行时刻伪装成业务事实。
-- 新写入由共享 JDBC 审计层在原业务事务内补齐；UTC 微秒时间来自数据库，账号只取可信执行主体。

ALTER TABLE `access_account_role`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_authorization_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_member_authorization`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_member_authorization_organization`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_member_authorization_permission`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_member_permission`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_member_role`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_permission`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定';

ALTER TABLE `access_permission_scope_type`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_policy_template`
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_role_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_role_data_policy`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_role_data_policy_organization`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_role_field_security`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `access_role_permission`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `account_security_log`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `identity_account`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定';

ALTER TABLE `identity_account_mfa`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `identity_bootstrap`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `identity_mfa_recovery`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `identity_password_recovery`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `identity_refresh_history`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `integration_call_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `integration_client`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `integration_client_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `integration_reference_request`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `organization_administrator_bootstrap`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `organization_capability_grant`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `organization_grant_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_administration_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_administrator`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_administrator_bootstrap`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_event_consumption`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_menu`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定';

ALTER TABLE `platform_menu_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_menu_resource`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_outbox`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_tenant`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `platform_tenant_creation`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_dictionary_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_dictionary_item_reference`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_invitation`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_member`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_member_primary_organization`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_organization_assignment`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_organization_leader`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_organization_position`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_organization_relation`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_organization_type`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定';

ALTER TABLE `tenant_organization_unit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';

ALTER TABLE `tenant_parameter`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入';

ALTER TABLE `tenant_parameter_audit`
  ADD COLUMN created_by BINARY(16) NULL DEFAULT NULL COMMENT '实际创建账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '创建时间，UTC，精确到微秒；历史未记录为空，新建由服务端写入',
  ADD COLUMN updated_by BINARY(16) NULL DEFAULT NULL COMMENT '最后修改账号 UUID；历史未知或系统执行为空，禁止客户端指定',
  ADD COLUMN updated_at TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后修改时间，UTC，精确到微秒；历史未记录为空，新建与修改由服务端写入';
