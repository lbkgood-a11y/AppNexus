-- 通知明细使用原 UUID 关联，保留通知、接管和实收关系及审计数据。
ALTER TABLE bw_inbound_notice_line
 DROP CHECK bw_inbound_notice_line_chk_1,
 DROP INDEX tenant_id,
 DROP COLUMN line_no,
 ADD CONSTRAINT chk_bw_inbound_notice_line_product_version CHECK (product_version >= 0);
