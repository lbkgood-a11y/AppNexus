-- 通知按可信租户与精确筛选项取候选，主键 UUID 支持稳定游标续页。
ALTER TABLE bw_inbound_notice
  ADD INDEX ix_bw_notice_warehouse_cursor (tenant_id,warehouse_id,id),
  ADD INDEX ix_bw_notice_owner_cursor (tenant_id,owner_id,id),
  ADD INDEX ix_bw_notice_upstream_cursor (tenant_id,appointment_no,id);
