-- 显式平台管理员与一次性初始化标记分离；撤权后重启不能恢复权限。
CREATE TABLE platform_administrator (
 account_id BINARY(16) NOT NULL PRIMARY KEY,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
-- 只有部署明确指定的现有有效账号可以占用唯一初始化槽。
CREATE TABLE platform_administrator_bootstrap (
 slot TINYINT NOT NULL PRIMARY KEY,
 account_id BINARY(16) NOT NULL,
 initialized_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_platform_bootstrap_slot CHECK(slot=1)
);
-- 平台配置写操作追加审计，不存密码、哈希或完整请求正文。
CREATE TABLE platform_administration_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_account_id BINARY(16) NOT NULL,
 action VARCHAR(40) NOT NULL,
 target_id BINARY(16) NOT NULL,
 tenant_id BINARY(16) NULL,
 occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
-- 创建租户的请求 ID 与载荷共同用于幂等判断，禁止重复网络提交创建第二个租户。
CREATE TABLE platform_tenant_creation (
 request_id BINARY(16) NOT NULL PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 actor_account_id BINARY(16) NOT NULL,
 administrator_account_id BINARY(16) NOT NULL,
 tenant_name VARCHAR(100) NOT NULL
);
