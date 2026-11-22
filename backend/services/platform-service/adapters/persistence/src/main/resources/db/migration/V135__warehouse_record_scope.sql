-- 发布仓库记录范围；新建仓库尚无记录 UUID，只支持显式全部仓库。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'ALL','masterdata.warehouse' FROM access_permission
 WHERE scope_type='TENANT' AND resource='masterdata.warehouse';
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'RULES','masterdata.warehouse' FROM access_permission
 WHERE scope_type='TENANT' AND code IN('masterdata.warehouse:read','masterdata.warehouse:update','masterdata.warehouse:set_status');

-- 已持有仓库功能权限的角色和成员保留先前的租户内全部范围；不增加功能授权。
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by)
 SELECT rp.role_id,rp.permission_id,'ALL','masterdata.warehouse',NULL FROM access_role_permission rp
 JOIN access_permission p ON p.id=rp.permission_id
 WHERE p.scope_type='TENANT' AND p.resource='masterdata.warehouse';

UPDATE access_member_authorization_permission ap JOIN access_permission p ON p.id=ap.permission_id
 SET ap.scope_type='ALL',ap.resource_type='masterdata.warehouse'
 WHERE p.scope_type='TENANT' AND p.resource='masterdata.warehouse' AND ap.scope_type IS NULL;
