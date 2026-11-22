-- 保存撤配后续建车次与原卷的关联，旧车次和普通车次保留空值。
ALTER TABLE bw_outbound_trip
  ADD COLUMN recovery_receipt_id BINARY(16) NULL COMMENT '由撤配后原授权续办绑定的整卷库存 UUID',
  ADD COLUMN recovery_closed_at TIMESTAMP(6) NULL COMMENT '续办车次撤配关闭的 UTC 时点，空值表示未撤配关闭',
  ADD INDEX idx_bw_trip_recovery(tenant_id,authorization_id,recovery_receipt_id,recovery_closed_at),
  ADD CONSTRAINT fk_bw_trip_recovery_receipt FOREIGN KEY(tenant_id,recovery_receipt_id)
    REFERENCES bw_stock_coil(tenant_id,receipt_id);
