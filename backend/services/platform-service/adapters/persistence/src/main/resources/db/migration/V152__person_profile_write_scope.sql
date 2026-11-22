-- 发布人员修改和启停的统一数据范围，原功能权限和写入入口保持不变。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT p.id,s.scope_type,'masterdata.person' FROM access_permission p
 CROSS JOIN (SELECT 'ALL' scope_type UNION ALL SELECT 'RULES') s
 WHERE p.scope_type='TENANT' AND p.code IN('masterdata.person:update','masterdata.person:set_status');

-- 只为已有相应权限的角色和独立成员保留历史全租户范围，不授予其他功能权限。
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by)
 SELECT rp.role_id,p.id,'ALL','masterdata.person',NULL FROM access_role_permission rp
 JOIN access_permission p ON p.id=rp.permission_id
 WHERE p.scope_type='TENANT' AND p.code IN('masterdata.person:update','masterdata.person:set_status');

UPDATE access_member_authorization_permission ap JOIN access_permission p ON p.id=ap.permission_id
 SET ap.scope_type='ALL',ap.resource_type='masterdata.person'
 WHERE p.scope_type='TENANT' AND p.code IN('masterdata.person:update','masterdata.person:set_status') AND ap.scope_type IS NULL;
