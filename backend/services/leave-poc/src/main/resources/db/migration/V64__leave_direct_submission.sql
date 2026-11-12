-- ACCEPTED 表示业务直接受理，区别于审批通过；状态仍由请假业务维护。
ALTER TABLE leave_poc_request DROP CHECK ck_leave_poc_state,
 ADD CONSTRAINT ck_leave_poc_state CHECK(state IN('DRAFT','SUBMITTED','ACCEPTED','APPROVED','REJECTED','WITHDRAWN'));
-- 所有提交保留业务执行记录，直接提交无流程轮次，技术重试不重复受理。
CREATE TABLE leave_poc_submission (
 tenant_id VARCHAR(64) NOT NULL,
 object_id VARCHAR(36) NOT NULL,
 basis_version BIGINT NOT NULL,
 resolution_id VARCHAR(36) NOT NULL,
 submission_mode VARCHAR(16) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,object_id,basis_version)
);
