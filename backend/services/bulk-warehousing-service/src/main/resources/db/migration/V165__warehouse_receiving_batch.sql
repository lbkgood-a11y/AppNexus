-- 批次号归属原实收，后续操作通过原实收 UUID 追溯；历史未知值保留 NULL。
ALTER TABLE bw_inbound_receipt ADD COLUMN receipt_batch_no VARCHAR(64) NULL
 COMMENT '仓库收货批次号，同一收货批次可由多条实收引用，历史未知为 NULL';
