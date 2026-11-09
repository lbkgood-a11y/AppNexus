-- 每条旧成员权限建立独立受保护迁移角色，保留成员、权限及各自到期时间，禁止跨授权分支拼接。
INSERT IGNORE INTO access_role(id,scope_type,tenant_id,role_type,system_key,code,name,description,status,sort_order,created_by)
SELECT UUID_TO_BIN(UUID()),'TENANT',mp.tenant_id,'SYSTEM',
       CONCAT('LEGACY:',LEFT(SHA2(CONCAT(HEX(mp.tenant_id),HEX(mp.member_id),mp.permission_code),256),56)),
       CONCAT('sys_legacy_',LEFT(SHA2(CONCAT(HEX(mp.member_id),mp.permission_code),256),32)),
       '旧组织授权迁移','由 V30 按原成员、权限和有效期迁移；不得手工分配或扩权。','ENABLED',9999,mp.granted_by
FROM access_member_permission mp JOIN access_permission p ON p.code=mp.permission_code AND p.scope_type='TENANT';

-- 迁移角色只包含原权限；组织单元权限沿用旧模型的当前租户全部范围。
INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by)
SELECT r.id,p.id,'TENANT',mp.granted_by FROM access_member_permission mp
JOIN access_permission p ON p.code=mp.permission_code AND p.scope_type='TENANT'
JOIN access_role r ON r.system_key=CONCAT('LEGACY:',LEFT(SHA2(CONCAT(HEX(mp.tenant_id),HEX(mp.member_id),mp.permission_code),256),56));
INSERT IGNORE INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by)
SELECT r.id,p.id,'ALL','organization.unit',mp.granted_by FROM access_member_permission mp
JOIN access_permission p ON p.code=mp.permission_code AND p.resource='organization.unit'
JOIN access_role r ON r.system_key=CONCAT('LEGACY:',LEFT(SHA2(CONCAT(HEX(mp.tenant_id),HEX(mp.member_id),mp.permission_code),256),56));

INSERT IGNORE INTO access_member_role(tenant_id,member_id,role_id,assigned_by)
SELECT mp.tenant_id,mp.member_id,r.id,mp.granted_by FROM access_member_permission mp
JOIN access_role r ON r.system_key=CONCAT('LEGACY:',LEFT(SHA2(CONCAT(HEX(mp.tenant_id),HEX(mp.member_id),mp.permission_code),256),56));
INSERT IGNORE INTO access_member_authorization(tenant_id,member_id,role_id,role_version,expires_at)
SELECT mp.tenant_id,mp.member_id,r.id,r.version,mp.expires_at FROM access_member_permission mp
JOIN access_role r ON r.system_key=CONCAT('LEGACY:',LEFT(SHA2(CONCAT(HEX(mp.tenant_id),HEX(mp.member_id),mp.permission_code),256),56));
INSERT IGNORE INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,scope_type,resource_type)
SELECT mp.tenant_id,mp.member_id,r.id,p.id,CASE WHEN p.resource='organization.unit' THEN 'ALL' ELSE NULL END,
       CASE WHEN p.resource='organization.unit' THEN 'organization.unit' ELSE NULL END
FROM access_member_permission mp JOIN access_permission p ON p.code=mp.permission_code
JOIN access_role r ON r.system_key=CONCAT('LEGACY:',LEFT(SHA2(CONCAT(HEX(mp.tenant_id),HEX(mp.member_id),mp.permission_code),256),56));
