-- 实际操作者与被代表人员分开；系统管理/后台任务可无人员，历史未知保持 NULL。
ALTER TABLE wf_audit ADD COLUMN person_id VARCHAR(36) NULL COMMENT '本次被代表人员 UUID，来自可信选择；历史及管理操作可空', ADD COLUMN business_organization_id VARCHAR(36) NULL COMMENT '本次人员业务组织 UUID，不替代流程单据组织';
