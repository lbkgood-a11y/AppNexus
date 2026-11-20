-- 审批回执归入对象本轮记录与轮次事实，不保留旧的逐对象交付表或兼容读取分支。
ALTER TABLE leave_poc_request
 ADD COLUMN last_delivery_id VARCHAR(128) NULL COMMENT '当前依据版本已应用的工作流交付标识；未完成或历史未知为NULL';
ALTER TABLE leave_poc_approval_cycle
 ADD COLUMN delivery_id VARCHAR(128) NULL COMMENT '本轮已应用的稳定交付标识；同租户唯一，未知为NULL',
 ADD COLUMN result VARCHAR(16) NULL COMMENT '本轮审批终态APPROVED、REJECTED或WITHDRAWN；未交付为NULL',
 ADD COLUMN completed_at TIMESTAMP(6) NULL COMMENT '数据库生成的UTC结果应用时间；历史未知为NULL',
 ADD UNIQUE KEY uq_leave_cycle_delivery(tenant_id,delivery_id);

-- 仅匹配真实实例，不能将旧轮次结果覆盖到已重提的新依据；历史完成时间不伪造。
UPDATE leave_poc_approval_cycle cycle
 JOIN leave_poc_delivery delivery ON delivery.tenant_id=cycle.tenant_id
  AND delivery.object_id=cycle.object_id AND delivery.instance_id=cycle.instance_id
 SET cycle.delivery_id=delivery.invocation_id,cycle.result=delivery.result;
UPDATE leave_poc_request request
 JOIN leave_poc_delivery delivery ON delivery.tenant_id=request.tenant_id
  AND delivery.object_id=request.id AND delivery.instance_id=request.instance_id
 JOIN leave_poc_approval_cycle cycle ON cycle.tenant_id=request.tenant_id
  AND cycle.object_id=request.id AND cycle.instance_id=request.instance_id AND cycle.basis_version=request.basis_version
 SET request.last_delivery_id=delivery.invocation_id;

DROP TABLE leave_poc_delivery;
