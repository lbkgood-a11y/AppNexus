-- 契约结构与发布/执行版本一起冻结；历史记录为 NULL，表示未记录，禁止伪造历史契约身份。
ALTER TABLE wf_version ADD COLUMN data_contract_schema JSON NULL COMMENT '发布时工作流数据契约快照；历史版本未记录时为空';
ALTER TABLE wf_instance ADD COLUMN data_contract_schema JSON NULL COMMENT '本次审批使用的数据契约快照；历史实例为空';
