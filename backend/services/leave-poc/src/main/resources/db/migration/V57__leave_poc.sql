-- 请假 POC 自有单据；所有读写显式限定租户，不向引擎复制请假原因。
CREATE TABLE leave_poc_request (
 tenant_id VARCHAR(64) NOT NULL,
 id VARCHAR(36) NOT NULL,
 applicant VARCHAR(64) NOT NULL,
 organization_id VARCHAR(64) NOT NULL,
 leave_type VARCHAR(16) NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 days INT NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 basis_version BIGINT NOT NULL DEFAULT 0,
 instance_id VARCHAR(64) NULL,
 create_key VARCHAR(36) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id),
 UNIQUE KEY uq_leave_poc_create(tenant_id,applicant,create_key),
 KEY ix_leave_poc_applicant(tenant_id,applicant,created_at),
 CONSTRAINT ck_leave_poc_days CHECK(days BETWEEN 1 AND 365),
 CONSTRAINT ck_leave_poc_state CHECK(state IN('DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN'))
);
-- 结果交付以稳定 invocationId 持久化幂等；与业务状态更新同事务提交。
CREATE TABLE leave_poc_delivery (
 tenant_id VARCHAR(64) NOT NULL,
 invocation_id VARCHAR(128) NOT NULL,
 object_id VARCHAR(36) NOT NULL,
 instance_id VARCHAR(64) NOT NULL,
 result VARCHAR(16) NOT NULL,
 PRIMARY KEY(tenant_id,invocation_id)
);
