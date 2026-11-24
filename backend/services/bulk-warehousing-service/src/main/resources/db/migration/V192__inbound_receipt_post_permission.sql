-- 登记独立入库过账权限，角色准入与范围由统一权限中心实际配置。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0192-7000-8000-000000000001'),'TENANT','warehousing.receipt:post',
 'warehousing.receipt','post','确认入库过账','Post inbound receipt',
 '重新核验当前合格质检、计量证据和通知额度后写入现有库存账');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.receipt:post';
