-- 登记独立质检写操作，角色及行范围由统一权限中心配置。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0188-7000-8000-000000000001'),'TENANT','warehousing.inboundquality:register_sample',
 'warehousing.inboundquality','register_sample','入库采样登记','Register inbound sample','登记样品或切换当前作业的复检版本');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.inboundquality:register_sample';
