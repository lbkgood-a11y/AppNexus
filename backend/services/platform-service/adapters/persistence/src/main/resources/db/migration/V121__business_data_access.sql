-- 字段限制扩展：READ_ONLY 保留原文但禁止写入；MASKED/HIDDEN 同时禁止写入，不提供原文授权。
ALTER TABLE access_role_field_security DROP CHECK ck_role_field_security_mode,
 MODIFY display_mode VARCHAR(16) NOT NULL COMMENT '强制字段限制：READ_ONLY原文只读、MASKED脱敏只读、HIDDEN隐藏只读；未配置不额外限制',
 ADD CONSTRAINT ck_role_field_security_mode CHECK(display_mode IN('READ_ONLY','MASKED','HIDDEN'));
-- 无人工操作者的系统迁移允许NULL，不伪造配置账号。
ALTER TABLE access_role_data_policy MODIFY configured_by BINARY(16) NULL COMMENT '策略配置账号UUID；可信认证账号，历史或系统迁移无账号时NULL';
-- 客商历史权限原本覆盖整个租户，迁移为显式 ALL 保持既有效果；新分配权限必须正常配置范围。
INSERT IGNORE INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT p.id,s.scope_type,'masterdata.partner' FROM access_permission p
 JOIN (SELECT 'ALL' scope_type UNION ALL SELECT 'ORGANIZATION' UNION ALL SELECT 'ORGANIZATION_SUBTREE') s
 WHERE p.scope_type='TENANT' AND p.code IN('masterdata.partner:read','masterdata.partner:create','masterdata.partner:update','masterdata.partner:set_status');
INSERT IGNORE INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by)
 SELECT rp.role_id,rp.permission_id,'ALL','masterdata.partner',NULL FROM access_role_permission rp
 JOIN access_permission p ON p.id=rp.permission_id WHERE p.resource='masterdata.partner' AND p.scope_type='TENANT';
INSERT IGNORE INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,scope_type,resource_type)
 SELECT g.tenant_id,g.member_id,g.role_id,rp.permission_id,'ALL','masterdata.partner' FROM access_member_authorization g
 JOIN access_role_permission rp ON rp.role_id=g.role_id JOIN access_permission p ON p.id=rp.permission_id
 WHERE p.resource='masterdata.partner' AND p.scope_type='TENANT';
UPDATE access_member_authorization_permission ap JOIN access_permission p ON p.id=ap.permission_id
 SET ap.scope_type='ALL',ap.resource_type='masterdata.partner'
 WHERE p.resource='masterdata.partner' AND ap.scope_type IS NULL;
