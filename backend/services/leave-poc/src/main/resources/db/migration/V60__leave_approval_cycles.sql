-- 业务模块拥有审批轮次和关联历史，中心不代替原单据状态。
CREATE TABLE leave_poc_approval_cycle (
 tenant_id VARCHAR(64) NOT NULL,
 id VARCHAR(36) NOT NULL,
 object_id VARCHAR(36) NOT NULL,
 basis_version BIGINT NOT NULL,
 instance_id VARCHAR(64) NOT NULL,
 resolution_id VARCHAR(36) NULL,
 configuration_version INT NULL,
 basis_snapshot JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id),
 UNIQUE KEY uq_leave_cycle_instance(tenant_id,instance_id),
 UNIQUE KEY uq_leave_cycle_basis(tenant_id,object_id,basis_version)
);
-- 保留已有实例关联；原因原文仅保存在业务自有历史快照中，禁止用于公共事件。
INSERT INTO leave_poc_approval_cycle(tenant_id,id,object_id,basis_version,instance_id,basis_snapshot)
 SELECT tenant_id,UUID(),id,basis_version,instance_id,
 JSON_OBJECT('leaveType',leave_type,'startDate',start_date,'endDate',end_date,'days',days,'reason',reason,'organizationId',organization_id)
 FROM leave_poc_request WHERE instance_id IS NOT NULL;
