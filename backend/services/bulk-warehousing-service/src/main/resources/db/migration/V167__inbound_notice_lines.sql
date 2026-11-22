-- 单据头保留旧 UUID。文本单号沿用原 UUID，便于旧链接和存量接口继续查询。
ALTER TABLE bw_inbound_notice
 ADD COLUMN notice_no VARCHAR(64) NULL COMMENT '租户内业务单号，历史沿用通知 UUID 文本',
 ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ISSUED' COMMENT '通知状态：草稿、签发、接收中、完成、关闭或撤销',
 ADD COLUMN issued_by BINARY(16) NULL COMMENT '可信签发账号 UUID，历史未知可空',
 ADD COLUMN issued_at TIMESTAMP(6) NULL COMMENT '数据库 UTC 签发时间，历史未知可空',
 ADD COLUMN expected_arrival_at TIMESTAMP(6) NULL COMMENT '计划到货 UTC 时间',
 ADD COLUMN remark VARCHAR(500) NULL COMMENT '通知接货要求或备注',
 ADD COLUMN termination_reason VARCHAR(500) NULL COMMENT '关闭或撤销原因',
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '通知头乐观锁版本',
 ADD CONSTRAINT ck_bw_inbound_notice_status CHECK (status IN ('DRAFT','ISSUED','RECEIVING','COMPLETED','CLOSED','CANCELLED')),
 ADD CONSTRAINT ck_bw_inbound_notice_version CHECK (version >= 0);

UPDATE bw_inbound_notice n
SET notice_no=LOWER(BIN_TO_UUID(n.id)), issued_by=n.created_by, issued_at=n.created_at,
    status=CASE WHEN EXISTS(SELECT 1 FROM bw_inbound_custody c WHERE c.tenant_id=n.tenant_id AND c.notice_id=n.id)
                    THEN 'RECEIVING' ELSE 'ISSUED' END;
ALTER TABLE bw_inbound_notice
 MODIFY COLUMN notice_no VARCHAR(64) NOT NULL COMMENT '租户内唯一业务单号',
 ADD UNIQUE KEY uk_bw_inbound_notice_no(tenant_id,notice_no);

CREATE TABLE bw_inbound_notice_line (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '通知明细 UUID',
 notice_id BINARY(16) NOT NULL COMMENT '来源通知 UUID',
 line_no INT NOT NULL COMMENT '通知内从 1 开始的稳定行号',
 product_id BINARY(16) NOT NULL COMMENT '父商品 UUID',
 identity_kind VARCHAR(16) NOT NULL COMMENT '商品、规格或 SKU 核算粒度',
 identity_id BINARY(16) NOT NULL COMMENT '所选商品身份 UUID',
 product_version BIGINT NOT NULL COMMENT '签发采用的父商品版本',
 base_unit_id BINARY(16) NOT NULL COMMENT '库存基础计量单位 UUID',
 planned_quantity DECIMAL(30,12) NULL COMMENT '基础单位计划量，未知时为空',
 approved_quantity DECIMAL(30,12) NOT NULL COMMENT '基础单位批准收货上限',
 received_quantity DECIMAL(30,12) NOT NULL DEFAULT 0 COMMENT '基础单位累计已过账实收量',
 remark VARCHAR(500) NULL COMMENT '该行的接收要求或备注',
 created_by BINARY(16) NULL COMMENT '可信创建账号 UUID，历史未知可空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 创建时间',
 updated_by BINARY(16) NULL COMMENT '可信最后更新账号 UUID，历史未知可空',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 最后更新时间',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,notice_id,line_no),
 UNIQUE(tenant_id,notice_id,id),
 CONSTRAINT fk_bw_notice_line_header FOREIGN KEY(tenant_id,notice_id) REFERENCES bw_inbound_notice(tenant_id,id),
 CHECK(line_no>0 AND product_version>=0),
 CHECK(identity_kind IN ('PRODUCT','SPECIFICATION','SKU')),
 CHECK(planned_quantity IS NULL OR planned_quantity>0),
 CHECK(approved_quantity>0 AND received_quantity>=0 AND received_quantity<=approved_quantity)
) COMMENT='入仓通知按商品身份和基础单位管理的独立额度';

-- 历史本身已经过账，保留余额、审计和证据，不重新消费计量凭证。
INSERT INTO bw_inbound_notice_line(tenant_id,id,notice_id,line_no,product_id,identity_kind,identity_id,
 product_version,base_unit_id,approved_quantity,received_quantity,created_by,created_at,updated_by,updated_at)
SELECT tenant_id,id,id,1,product_id,identity_kind,identity_id,product_version,base_unit_id,
 approved_quantity,received_quantity,created_by,created_at,updated_by,updated_at FROM bw_inbound_notice;

ALTER TABLE bw_inbound_custody
 ADD COLUMN notice_line_id BINARY(16) NULL COMMENT '当前接管归属的通知明细 UUID',
 ADD COLUMN arrival_batch_no VARCHAR(64) NULL COMMENT '同车或同船段共享的到货批号，历史未知为空';
UPDATE bw_inbound_custody SET notice_line_id=notice_id;
ALTER TABLE bw_inbound_custody
 MODIFY COLUMN notice_line_id BINARY(16) NOT NULL COMMENT '当前接管归属的通知明细 UUID',
 ADD CONSTRAINT fk_bw_custody_notice_line FOREIGN KEY(tenant_id,notice_id,notice_line_id)
    REFERENCES bw_inbound_notice_line(tenant_id,notice_id,id);

-- 兼容列仅镜像旧式单行通知，正式库存及实收读写改用明细。
ALTER TABLE bw_inbound_notice
 MODIFY COLUMN product_id BINARY(16) NULL COMMENT '兼容单行通知的父商品 UUID',
 MODIFY COLUMN identity_kind VARCHAR(16) NULL COMMENT '兼容单行通知的身份粒度',
 MODIFY COLUMN identity_id BINARY(16) NULL COMMENT '兼容单行通知的商品身份 UUID',
 MODIFY COLUMN product_version BIGINT NULL COMMENT '兼容单行通知的商品版本',
 MODIFY COLUMN base_unit_id BINARY(16) NULL COMMENT '兼容单行通知的库存基础单位 UUID',
 MODIFY COLUMN approved_quantity DECIMAL(30,12) NULL COMMENT '兼容单行通知的批准上限，多行无总额度',
 MODIFY COLUMN received_quantity DECIMAL(30,12) NULL DEFAULT NULL COMMENT '兼容单行通知的累计已收，多行按明细核算';
