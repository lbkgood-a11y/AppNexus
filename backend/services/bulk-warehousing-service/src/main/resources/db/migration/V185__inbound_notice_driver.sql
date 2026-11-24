-- 司机信息是入仓通知的可选运输联系快照；历史未知值保留NULL，不伪造联系人。
ALTER TABLE bw_inbound_notice
 ADD COLUMN driver_name VARCHAR(64) NULL COMMENT '可选司机姓名，最多64字符；按统一字段权限控制',
 ADD COLUMN driver_phone VARCHAR(32) NULL COMMENT '可选司机联系电话，最多32字符，允许国际区号和分机；按统一字段权限控制',
 ADD COLUMN driver_id_number VARCHAR(64) NULL COMMENT '可选司机证件号，最多64字符，不限定证件国家或类型；按统一字段权限控制';
