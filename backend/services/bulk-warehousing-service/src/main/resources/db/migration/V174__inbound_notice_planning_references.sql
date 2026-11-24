-- 可选计划引用，历史通知保留 NULL。签发及查询继续使用原主从租户边界。
ALTER TABLE bw_inbound_notice
  ADD COLUMN appointment_no VARCHAR(64) NULL COMMENT '外部预约单号，仅供查阅',
  ADD COLUMN planned_conveyance VARCHAR(64) NULL COMMENT '计划车牌或船号，实际到场信息以入场单为准';

ALTER TABLE bw_inbound_notice_line
  ADD COLUMN contract_no VARCHAR(64) NULL COMMENT '本行关联的业务合同号，仅供查阅';
