-- 现有通知固定续办 V1。新通知由应用保存签发时选中的版本，历史通知不随发布切换。
ALTER TABLE bw_inbound_notice
 ADD COLUMN flow_version INT NOT NULL DEFAULT 1 COMMENT '入仓流程绑定版本，从1开始，存量为1',
 ADD CONSTRAINT ck_bw_inbound_flow_version CHECK (flow_version > 0);

ALTER TABLE bw_outbound_notice
 ADD COLUMN flow_version INT NOT NULL DEFAULT 1 COMMENT '钢卷出仓流程绑定版本，从1开始，存量为1',
 ADD CONSTRAINT ck_bw_outbound_flow_version CHECK (flow_version > 0);
