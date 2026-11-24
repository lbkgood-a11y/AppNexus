-- 登记过账前质检判定的独立操作，实际角色及范围由统一授权中心配置。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0189-7000-8000-000000000001'),'TENANT','warehousing.inboundquality:decide_quality',
 'warehousing.inboundquality','decide_quality','判定入库质量','Decide inbound quality','按作业固定实验室规则写入当前质检版本的判定及快照');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.inboundquality:decide_quality';
