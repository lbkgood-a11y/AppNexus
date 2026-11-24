-- 把库存总览所需维度补齐到现有库存账，保留余额、货权生效语义和原审计。
ALTER TABLE bw_stock_receipt
 ADD COLUMN origin_warehouse_id BINARY(16) NULL COMMENT '原接管仓库 UUID，当前仓库缺省时使用',
 ADD COLUMN origin_owner_id BINARY(16) NULL COMMENT '通知原货主 UUID，登记未生效时使用',
 ADD COLUMN title_owner_id BINARY(16) NULL COMMENT '库存登记货主 UUID，未登记为空',
 ADD COLUMN title_effective_at TIMESTAMP(6) NULL COMMENT '登记货权的 UTC 生效时间，未登记为空',
 ADD COLUMN product_id BINARY(16) NULL COMMENT '库存商品 UUID',
 ADD COLUMN identity_kind VARCHAR(24) NULL COMMENT '库存商品身份类型，允许 PRODUCT、SPECIFICATION、SKU',
 ADD COLUMN identity_id BINARY(16) NULL COMMENT '库存商品身份 UUID',
 ADD COLUMN unit_id BINARY(16) NULL COMMENT '库存基础计量单位 UUID',
 ADD COLUMN receipt_batch_no VARCHAR(64) NULL COMMENT '库存批次号，历史未填写时为空',
 ADD COLUMN serial VARCHAR(64) NULL COMMENT '库存卷号，未登记时为空';

UPDATE bw_stock_receipt s
 JOIN bw_inbound_receipt r ON r.tenant_id=s.tenant_id AND r.id=s.receipt_id
 JOIN bw_inbound_custody c ON c.tenant_id=r.tenant_id AND c.id=r.custody_id
 JOIN bw_inbound_notice n ON n.tenant_id=c.tenant_id AND n.id=c.notice_id
 JOIN bw_inbound_notice_line l ON l.tenant_id=c.tenant_id AND l.notice_id=c.notice_id AND l.id=c.notice_line_id
 LEFT JOIN bw_inbound_title t ON t.tenant_id=s.tenant_id AND t.receipt_id=s.receipt_id
 LEFT JOIN bw_stock_coil k ON k.tenant_id=s.tenant_id AND k.receipt_id=s.receipt_id
 SET s.origin_warehouse_id=c.warehouse_id,s.origin_owner_id=n.owner_id,
     s.title_owner_id=t.owner_id,s.title_effective_at=t.effective_at,s.product_id=l.product_id,
     s.identity_kind=l.identity_kind,s.identity_id=l.identity_id,s.unit_id=r.unit_id,
     s.receipt_batch_no=r.receipt_batch_no,s.serial=k.serial;

-- 确认必需库存维度全部回填后，解除指向待删除实收表的外键。
ALTER TABLE bw_stock_receipt
 MODIFY COLUMN origin_warehouse_id BINARY(16) NOT NULL COMMENT '原接管仓库 UUID，当前仓库缺省时使用',
 MODIFY COLUMN origin_owner_id BINARY(16) NOT NULL COMMENT '通知原货主 UUID，登记未生效时使用',
 MODIFY COLUMN product_id BINARY(16) NOT NULL COMMENT '库存商品 UUID',
 MODIFY COLUMN identity_kind VARCHAR(24) NOT NULL COMMENT '库存商品身份类型，允许 PRODUCT、SPECIFICATION、SKU',
 MODIFY COLUMN identity_id BINARY(16) NOT NULL COMMENT '库存商品身份 UUID',
 MODIFY COLUMN unit_id BINARY(16) NOT NULL COMMENT '库存基础计量单位 UUID',
 DROP FOREIGN KEY bw_stock_receipt_ibfk_1;

-- 在途明细与短损、改仓记录互相引用，先解除指向活动记录的两个外键。
ALTER TABLE bw_coil_return_transit_line
 DROP FOREIGN KEY fk_bw_transit_line_active_loss,
 DROP FOREIGN KEY fk_bw_transit_line_active_reroute;

-- 先删除从表，再删除单据主表及其专用支撑表。
DROP TABLE bw_coil_handover_reversal;
DROP TABLE bw_coil_return_line;
DROP TABLE bw_coil_return_loss_reopening;
DROP TABLE bw_coil_return_transit_reroute;
DROP TABLE bw_coil_title_entry;
DROP TABLE bw_coil_weigh_line;
DROP TABLE bw_inbound_quality_release;
DROP TABLE bw_inbound_receipt_document_line;
DROP TABLE bw_inbound_title;
DROP TABLE bw_stock_restriction;
DROP TABLE bw_title_watermark;
DROP TABLE bw_coil_return_acceptance;
DROP TABLE bw_coil_return_transit_loss;
DROP TABLE bw_coil_title_transfer;
DROP TABLE bw_inbound_receipt;
DROP TABLE bw_inbound_receipt_document;
DROP TABLE bw_coil_return_transit_line;
DROP TABLE bw_inbound_custody;
DROP TABLE bw_coil_partial_return_line;
DROP TABLE bw_coil_return_transit;
DROP TABLE bw_inbound_entry;
DROP TABLE bw_coil_handover_line;
DROP TABLE bw_coil_partial_return;
DROP TABLE bw_coil_allocation;
DROP TABLE bw_coil_handover;
DROP TABLE bw_coil_weigh_review;
DROP TABLE bw_stock_occupation;
DROP TABLE bw_outbound_trip;
DROP TABLE bw_pickup_authorization;
DROP TABLE bw_stock_coil;
DROP TABLE bw_outbound_notice;
DROP TABLE bw_outbound_warehouse_guard;
