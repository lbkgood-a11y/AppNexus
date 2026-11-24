-- 聚合只读操作由作业及三单各自的统一对象声明提供，管理者独立授权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
 (UUID_TO_BIN('019a65d0-0202-7000-8000-000000000001'),'TENANT','warehousing.inboundtask:read_workbench_detail',
  'warehousing.inboundtask','read_workbench_detail','查询入库工作台详情','Read inbound workbench detail','查看无身份基础作业和授权卡片'),
 (UUID_TO_BIN('019a65d0-0202-7000-8000-000000000002'),'TENANT','warehousing.inboundtask:read_workbench_identity',
  'warehousing.inboundtask','read_workbench_identity','查询非盲检作业身份','Read non-blind inbound identity','非盲检阶段经字段保护后查看身份'),
 (UUID_TO_BIN('019a65d0-0202-7000-8000-000000000003'),'TENANT','warehousing.inboundquality:read_workbench_quality',
  'warehousing.inboundquality','read_workbench_quality','查询质检卡片','Read workbench quality card','查看授权作业的最小质检状态'),
 (UUID_TO_BIN('019a65d0-0202-7000-8000-000000000004'),'TENANT','warehousing.inboundmetering:read_workbench_meter',
  'warehousing.inboundmetering','read_workbench_meter','查询计量卡片','Read workbench metering card','查看授权作业的已记录计量'),
 (UUID_TO_BIN('019a65d0-0202-7000-8000-000000000005'),'TENANT','warehousing.receipt:read_workbench_receipt',
  'warehousing.receipt','read_workbench_receipt','查询入库卡片','Read workbench receipt card','查看授权作业的当前入库单及明细');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission WHERE scope_type='TENANT' AND id IN
 (UUID_TO_BIN('019a65d0-0202-7000-8000-000000000001'),UUID_TO_BIN('019a65d0-0202-7000-8000-000000000002'),
  UUID_TO_BIN('019a65d0-0202-7000-8000-000000000003'),UUID_TO_BIN('019a65d0-0202-7000-8000-000000000004'),
  UUID_TO_BIN('019a65d0-0202-7000-8000-000000000005'));
