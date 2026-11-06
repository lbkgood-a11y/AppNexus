-- 每租户仅允许一次显式管理员初始化；撤权后保留记录，禁止重启恢复权限。
CREATE TABLE organization_administrator_bootstrap (
 tenant_id BINARY(16) NOT NULL PRIMARY KEY,
 member_id BINARY(16) NOT NULL,
 initialized_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
