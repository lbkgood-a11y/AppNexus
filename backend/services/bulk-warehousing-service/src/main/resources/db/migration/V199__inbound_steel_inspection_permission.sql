-- 钢铁质保书与外观核验采用独立功能权限和租户范围，不默认扩大岗位授权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0199-7000-8000-000000000001'),'TENANT','warehousing.inboundquality:inspect_steel',
 'warehousing.inboundquality','inspect_steel','核验钢铁实物和质保书','Inspect inbound steel',
 '核验同一作业的可用质保书、炉批和实物外观，保存质量判定');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.inboundquality:inspect_steel';
