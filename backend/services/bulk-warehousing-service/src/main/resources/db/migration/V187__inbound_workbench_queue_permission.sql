-- 只登记工作台队列读取操作。租户管理员经统一授权中心发放权限及 ALL 行范围。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES (UUID_TO_BIN('019a65d0-0187-7000-8000-000000000001'),'TENANT',
 'warehousing.inboundtask:read_workbench_queue','warehousing.inboundtask','read_workbench_queue',
 '查询入库作业队列','Read inbound work queue','查询当前获准租户的作业队列及阶段计数');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission
WHERE scope_type='TENANT' AND code='warehousing.inboundtask:read_workbench_queue';
