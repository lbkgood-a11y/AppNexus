-- 请假记录区分实际执行账号与人员；存量业务人员未知为 NULL，不冒充历史身份。
ALTER TABLE leave_poc_request ADD COLUMN person_id VARCHAR(36) NULL COMMENT '被代表人员 UUID，新增业务必须来自可信人员选择；存量未知 NULL';
