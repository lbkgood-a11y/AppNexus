-- 人员明细新增、编辑、删除及账号关联共用父档案范围，沿用各自已有的功能权限。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT p.id,s.scope_type,'masterdata.person' FROM access_permission p
 CROSS JOIN (SELECT 'ALL' scope_type UNION ALL SELECT 'RULES') s
 WHERE p.scope_type='TENANT' AND p.code IN('masterdata.person:detail_create','masterdata.person:detail_update','masterdata.person:detail_delete','masterdata.person:assign');

-- 仅将既有授权的原租户内全量语义显式保留；不增加角色或成员的功能权限。
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by)
 SELECT rp.role_id,p.id,'ALL','masterdata.person',NULL FROM access_role_permission rp
 JOIN access_permission p ON p.id=rp.permission_id
 WHERE p.scope_type='TENANT' AND p.code IN('masterdata.person:detail_create','masterdata.person:detail_update','masterdata.person:detail_delete','masterdata.person:assign');

UPDATE access_member_authorization_permission ap JOIN access_permission p ON p.id=ap.permission_id
 SET ap.scope_type='ALL',ap.resource_type='masterdata.person'
 WHERE p.scope_type='TENANT' AND p.code IN('masterdata.person:detail_create','masterdata.person:detail_update','masterdata.person:detail_delete','masterdata.person:assign')
 AND ap.scope_type IS NULL;
