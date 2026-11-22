-- 入场主单归属于已签发通知；同一车辆或船段的实际接管明细分别引用通知行。
CREATE TABLE bw_inbound_entry (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '入场接管单 UUID，页面以此查询',
 notice_id BINARY(16) NOT NULL COMMENT '该车船次对应的来源通知 UUID',
 vehicle_number VARCHAR(64) NOT NULL COMMENT '实际车牌、船次或受控入场载具编号',
 carrier VARCHAR(200) NULL COMMENT '承运方名称，未知时为空',
 arrived_at TIMESTAMP(6) NOT NULL COMMENT '经办人记录的实际入场时间，按 UTC 保存',
 remark VARCHAR(500) NULL COMMENT '入场或接管备注',
 request_digest CHAR(64) NOT NULL COMMENT '批次原始提交规范摘要，用于重试一致性',
 created_by BINARY(16) NULL COMMENT '可信接管登记账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 创建时间',
 updated_by BINARY(16) NULL COMMENT '可信最后更新账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 最后更新时间',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,notice_id,id),
 CONSTRAINT fk_bw_entry_notice FOREIGN KEY(tenant_id,notice_id) REFERENCES bw_inbound_notice(tenant_id,id)
) COMMENT='入场接管单主表，一车或船段可关联合同通知的多个品规';

-- 已有接管行及 UUID 继续作为明细；旧单行接口无需虚构入场主单。
ALTER TABLE bw_inbound_custody
 ADD COLUMN entry_id BINARY(16) NULL COMMENT '来源入场接管单 UUID，旧接管可空',
 ADD COLUMN expected_quantity DECIMAL(30,12) NULL COMMENT '基础单位到货记录量，计量未定时为空',
 ADD UNIQUE KEY uk_bw_custody_entry_line(tenant_id,entry_id,id),
 ADD CONSTRAINT fk_bw_custody_entry FOREIGN KEY(tenant_id,notice_id,entry_id)
    REFERENCES bw_inbound_entry(tenant_id,notice_id,id),
 ADD CONSTRAINT ck_bw_custody_expected CHECK (expected_quantity IS NULL OR expected_quantity > 0);

-- 一个入场单可以分批实收。确认单头保存本次采用的计量单号与审核轨迹。
CREATE TABLE bw_inbound_receipt_document (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '实收确认单 UUID',
 entry_id BINARY(16) NOT NULL COMMENT '来源入场接管单 UUID',
 measurement_ref VARCHAR(64) NULL COMMENT '外部磅单或计量单号，不替代明细计量凭证',
 remark VARCHAR(500) NULL COMMENT '本次实收说明',
 request_digest CHAR(64) NOT NULL COMMENT '批次原始提交规范摘要，用于重试一致性',
 created_by BINARY(16) NULL COMMENT '可信实收确认账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 创建时间',
 updated_by BINARY(16) NULL COMMENT '可信最后更新账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 最后更新时间',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,entry_id,id),
 CONSTRAINT fk_bw_receipt_document_entry FOREIGN KEY(tenant_id,entry_id) REFERENCES bw_inbound_entry(tenant_id,id)
) COMMENT='实收确认单主表，关联同一车船入场批次';

ALTER TABLE bw_inbound_receipt
 ADD UNIQUE KEY uk_bw_receipt_custody_posting(tenant_id,custody_id,id);

-- 明细允许全拒收。只有合格实收产生原库存过账，拒收数量不消耗通知的批准额度。
CREATE TABLE bw_inbound_receipt_document_line (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '实收确认明细 UUID，正实收时同时是原实收过账 UUID',
 document_id BINARY(16) NOT NULL COMMENT '实收确认单主表 UUID',
 entry_id BINARY(16) NOT NULL COMMENT '与确认单及接管一致的入场主单 UUID',
 custody_id BINARY(16) NOT NULL COMMENT '该接管范围 UUID，同一范围仅确认一次',
 receipt_id BINARY(16) NULL COMMENT '正实收对应的原实收过账 UUID，全拒收为空',
 received_quantity DECIMAL(30,12) NOT NULL COMMENT '基础单位合格实收量',
 rejected_quantity DECIMAL(30,12) NOT NULL COMMENT '基础单位拒收量',
 rejection_reason VARCHAR(500) NULL COMMENT '拒收时填写的原因',
 created_by BINARY(16) NULL COMMENT '可信实收确认账号 UUID',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 创建时间',
 updated_by BINARY(16) NULL COMMENT '可信最后更新账号 UUID',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库 UTC 最后更新时间',
 PRIMARY KEY(tenant_id,id),
 UNIQUE(tenant_id,custody_id),
 KEY idx_bw_receipt_document_lines(tenant_id,document_id,id),
 CONSTRAINT fk_bw_receipt_line_document FOREIGN KEY(tenant_id,entry_id,document_id)
    REFERENCES bw_inbound_receipt_document(tenant_id,entry_id,id),
 CONSTRAINT fk_bw_receipt_line_entry_custody FOREIGN KEY(tenant_id,entry_id,custody_id)
    REFERENCES bw_inbound_custody(tenant_id,entry_id,id),
 CONSTRAINT fk_bw_receipt_line_posting FOREIGN KEY(tenant_id,custody_id,receipt_id)
    REFERENCES bw_inbound_receipt(tenant_id,custody_id,id),
 CHECK(received_quantity>=0 AND rejected_quantity>=0 AND (received_quantity>0 OR rejected_quantity>0)),
 CHECK((received_quantity>0 AND receipt_id IS NOT NULL) OR (received_quantity=0 AND receipt_id IS NULL)),
 CHECK(rejected_quantity=0 OR (rejection_reason IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_reason))>0))
) COMMENT='实收确认明细，保留各品规合格与拒收结果';

-- 菜单继续沿用仓储单据读取权限，不增加角色授权。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
    permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0168-7000-8000-000000000001'),id,'MENU',
    'builtin.navigation.warehousing.inbound-entries','入场接管单','Inbound entries',
    '/warehousing/inbound-entries','warehousing/inbound-entries','ri/file-list-line',
    'warehousing.coil.stockbalance:read',2,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY';

-- 现有确认路由及兼容旧编号保留，正式名称对齐实际实收动作。
UPDATE platform_menu SET title_zh='实收确认单',sort_order=3,version=version+1,
    updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
WHERE code='builtin.navigation.warehousing.inbound-receipts' AND scope_type='TENANT';
