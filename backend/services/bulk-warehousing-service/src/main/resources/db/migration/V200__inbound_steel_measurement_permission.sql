-- 钢铁双计量独立于质检、一般地磅和外部散货权限。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0200-7000-8000-000000000001'),'TENANT','warehousing.inboundmetering:confirm_steel',
 'warehousing.inboundmetering','confirm_steel','确认钢铁双计量','Confirm inbound steel measurement',
 '核验钢铁件数、实重、理计和模板允许磅差');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.inboundmetering:confirm_steel';
