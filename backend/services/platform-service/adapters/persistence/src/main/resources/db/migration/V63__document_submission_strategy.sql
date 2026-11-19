-- 旧解析默认审批；直接提交不伪造流程 ID，关联单据行为固定为 submit。
ALTER TABLE doc_resolution
 ADD COLUMN action_code VARCHAR(64) NOT NULL DEFAULT 'submit',
 ADD COLUMN submission_mode VARCHAR(16) NOT NULL DEFAULT 'APPROVAL',
 MODIFY definition_id VARCHAR(64) NULL,
 MODIFY definition_version INT NULL;
