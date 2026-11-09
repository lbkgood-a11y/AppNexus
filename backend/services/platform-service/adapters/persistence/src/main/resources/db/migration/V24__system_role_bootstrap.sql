-- 平台超级管理员角色使用固定 UUID，保证所有环境中的受控系统引用一致。
INSERT IGNORE INTO access_role(id,scope_type,tenant_id,role_type,system_key,code,name,description,status,sort_order)
VALUES(UUID_TO_BIN('019a65d0-0000-7000-9000-000000000001'),'PLATFORM',NULL,'SYSTEM','PLATFORM_SUPER_ADMIN','sys_platform_super_admin','平台超级管理员','平台受控初始化角色；拥有全部已发布平台权限。','ENABLED',0);

-- 每个租户拥有独立系统管理员角色；system_key 编入租户 UUID，满足全局不可变唯一约束。
INSERT IGNORE INTO access_role(id,scope_type,tenant_id,role_type,system_key,code,name,description,status,sort_order)
SELECT UUID_TO_BIN(UUID()),'TENANT',t.id,'SYSTEM',CONCAT('TENANT_ADMIN:',BIN_TO_UUID(t.id)),'sys_tenant_admin','租户管理员','租户受控初始化角色；拥有全部已发布租户权限。','ENABLED',0
FROM platform_tenant t;

-- 系统管理员角色权限由受控发布维护；平台与租户管理域不能交叉。
INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
SELECT r.id,p.id,r.scope_type,COALESCE((SELECT account_id FROM platform_administrator_bootstrap WHERE slot=1),0x00000000000000000000000000000000)
FROM access_role r JOIN access_permission p ON p.scope_type=r.scope_type AND p.enabled=TRUE
WHERE r.system_key='PLATFORM_SUPER_ADMIN' OR r.system_key LIKE 'TENANT_ADMIN:%';

-- 需要数据范围的租户管理员权限默认限定为当前租户 ALL，不表示跨租户全部。
INSERT IGNORE INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by)
SELECT rp.role_id,rp.permission_id,'ALL',st.resource_type,rp.granted_by
FROM access_role_permission rp JOIN access_role r ON r.id=rp.role_id
JOIN access_permission_scope_type st ON st.permission_id=rp.permission_id AND st.scope_type='ALL'
WHERE r.system_key LIKE 'TENANT_ADMIN:%';

-- 把现有过渡权威映射到系统角色；保留旧记录用于双读对账，不在本迁移中撤销旧授权。
INSERT IGNORE INTO access_account_role(account_id,role_id,scope_type,assigned_by)
SELECT pa.account_id,r.id,'PLATFORM',pa.account_id FROM platform_administrator pa
JOIN access_role r ON r.system_key='PLATFORM_SUPER_ADMIN' WHERE pa.enabled=TRUE;

INSERT IGNORE INTO access_member_role(tenant_id,member_id,role_id,assigned_by)
SELECT b.tenant_id,b.member_id,r.id,b.member_id FROM organization_administrator_bootstrap b
JOIN access_role r ON r.tenant_id=b.tenant_id AND r.code='sys_tenant_admin';
