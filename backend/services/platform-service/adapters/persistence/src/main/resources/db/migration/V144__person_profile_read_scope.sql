-- 人员档案列表和详情共用查看权限；发布全部及统一组合范围，不新增功能授权。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT p.id,s.scope_type,'masterdata.person' FROM access_permission p
 CROSS JOIN (SELECT 'ALL' scope_type UNION ALL SELECT 'RULES') s
 WHERE p.scope_type='TENANT' AND p.code='masterdata.person:read';

-- 现有具备查看权限的角色与独立成员保留既有租户内全量语义。
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by)
 SELECT rp.role_id,p.id,'ALL','masterdata.person',NULL FROM access_role_permission rp
 JOIN access_permission p ON p.id=rp.permission_id
 WHERE p.scope_type='TENANT' AND p.code='masterdata.person:read';

UPDATE access_member_authorization_permission ap JOIN access_permission p ON p.id=ap.permission_id
 SET ap.scope_type='ALL',ap.resource_type='masterdata.person'
 WHERE p.scope_type='TENANT' AND p.code='masterdata.person:read' AND ap.scope_type IS NULL;
