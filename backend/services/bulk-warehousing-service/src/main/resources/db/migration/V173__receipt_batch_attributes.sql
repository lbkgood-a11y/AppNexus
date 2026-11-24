-- 只保存新实收采用的批次模板及已校验字段值；历史实收保留 NULL。
ALTER TABLE bw_inbound_receipt ADD COLUMN batch_attribute_snapshot JSON NULL
 COMMENT '实收批次属性快照，含采用的模板版本及校验后字段值；NULL 表示未配置或历史未知';
