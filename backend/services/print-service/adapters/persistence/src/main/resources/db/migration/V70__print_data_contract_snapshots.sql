-- 输出契约随模板版本及任务冻结；历史记录为空，不伪造历史身份。
ALTER TABLE print_template_version ADD COLUMN data_contract_schema JSON NULL COMMENT '发布时打印数据契约快照；历史版本为空';
ALTER TABLE print_job ADD COLUMN data_contract_schema JSON NULL COMMENT '输出采用的契约快照；重印保持原快照，历史任务为空';
