-- 人工复核的地磅原始票据。毛皮共用唯一认领键，避免一个纸质票号多次使用。
CREATE TABLE bw_ground_weigh_ticket (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
 id BINARY(16) NOT NULL COMMENT '票据登记 UUID',
 task_id BINARY(16) NOT NULL COMMENT '本次过磅作业 UUID',
 metering_id BINARY(16) NOT NULL COMMENT '同作业计量单 UUID',
 reading_phase VARCHAR(12) NOT NULL COMMENT '毛重 GROSS 或皮重 TARE',
 station_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '人工核对的出具站点标识，忽略大小写',
 ticket_no VARCHAR(96) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '出具站点内唯一原始票号，忽略大小写',
 observed_at TIMESTAMP(6) NOT NULL COMMENT '票证记录的实际 UTC 读数时点，微秒精度',
 weight_kg DECIMAL(18,4) NOT NULL COMMENT '票证正重量，千克，四位小数',
 created_by BINARY(16) NULL COMMENT '可信复核登记账号 UUID，系统无账号时为空',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
 updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID，初始与创建人一致',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间',
 PRIMARY KEY(tenant_id,id),
 UNIQUE KEY uk_bw_ground_ticket(tenant_id,station_code,ticket_no),
 UNIQUE KEY uk_bw_ground_meter_phase(tenant_id,metering_id,reading_phase),
 CONSTRAINT fk_bw_ground_meter FOREIGN KEY(tenant_id,task_id,metering_id)
   REFERENCES bw_metering_order(tenant_id,task_id,id),
 CHECK(reading_phase IN ('GROSS','TARE')),
 CHECK(weight_kg>0)
) COMMENT='入库地磅人工复核票据';

-- 三个操作使用各自的统一权限。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
 (UUID_TO_BIN('019a65d0-0197-7000-8000-000000000001'),'TENANT','warehousing.inboundmetering:record_gross',
  'warehousing.inboundmetering','record_gross','登记毛重','Record inbound gross weight','人工复核原毛重票证'),
 (UUID_TO_BIN('019a65d0-0197-7000-8000-000000000002'),'TENANT','warehousing.inboundmetering:record_tare',
  'warehousing.inboundmetering','record_tare','登记皮重','Record inbound tare weight','按现行质检和监卸门槛复核皮重票证'),
 (UUID_TO_BIN('019a65d0-0197-7000-8000-000000000003'),'TENANT','warehousing.inboundtask:confirm_unloading',
  'warehousing.inboundtask','confirm_unloading','确认入库监卸','Confirm inbound unloading','保管员确认卸货、异常扣重或退车');

INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission WHERE scope_type='TENANT'
  AND code IN ('warehousing.inboundmetering:record_gross','warehousing.inboundmetering:record_tare','warehousing.inboundtask:confirm_unloading');
