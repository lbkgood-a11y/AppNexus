-- 登记散货入库草稿的独立权限，角色准入和范围由统一权限中心配置。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0191-7000-8000-000000000001'),'TENANT','warehousing.receipt:create_bulk_draft',
 'warehousing.receipt','create_bulk_draft','创建散货入库草稿','Create bulk receipt draft',
 '按有效外部计量、通知和主数据生成散货入库草稿');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.receipt:create_bulk_draft';
