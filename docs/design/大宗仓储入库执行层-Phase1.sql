-- Phase 1 评审草案，按现有 Flyway 结构设计。数据库连接使用 UTC。
-- 生效的授权范围、状态迁移和快照不可变性由应用事务执行。

CREATE TABLE bw_commodity_template (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
  id BINARY(16) NOT NULL COMMENT '入库执行模板版本 UUID',
  template_code VARCHAR(64) NOT NULL COMMENT '租户内模板逻辑编码',
  version INT UNSIGNED NOT NULL COMMENT '发布版本，从 1 递增',
  status VARCHAR(24) NOT NULL COMMENT '草稿、已发布或已停用',
  metering_strategy VARCHAR(40) NOT NULL COMMENT '服务端登记的计量策略键',
  quality_gate_strategy VARCHAR(40) NOT NULL COMMENT '服务端登记的质检放行策略键',
  deduction_strategy VARCHAR(40) NOT NULL COMMENT '服务端登记的扣重策略键',
  stock_granularity VARCHAR(24) NOT NULL COMMENT '库存粒度，BULK 或 LPN',
  definition JSON NOT NULL COMMENT '流程节点、表单 Schema、策略参数及主数据模板引用',
  row_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始，每次变更递增',
  created_by BINARY(16) NULL COMMENT '可信创建账号 UUID，系统执行无账号时为空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID，系统执行无账号时为空',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间',
  PRIMARY KEY (tenant_id, id),
  UNIQUE KEY uk_bw_exec_template_version (tenant_id, template_code, version),
  KEY idx_bw_exec_template_status (tenant_id, status, template_code),
  CHECK (version > 0 AND stock_granularity IN ('BULK', 'LPN'))
) ENGINE=InnoDB COMMENT='入库执行模板版本';

CREATE TABLE bw_inbound_task (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
  id BINARY(16) NOT NULL COMMENT '入库作业单 UUID',
  task_no VARCHAR(64) NOT NULL COMMENT '租户内作业单编码',
  notice_id BINARY(16) NOT NULL COMMENT '来源入仓通知 UUID',
  template_id BINARY(16) NOT NULL COMMENT '固定采用的入库模板版本 UUID',
  vehicle_identity_type VARCHAR(16) NOT NULL COMMENT '身份类型：PLATED 有牌、UNPLATED 无牌农用车、OTHER 非车辆作业',
  vehicle_plate VARCHAR(32) NULL COMMENT '有牌车辆的实际车牌',
  temporary_vehicle_no VARCHAR(64) NULL COMMENT '无牌农用车的现场临时识别号',
  card_id BINARY(16) NULL COMMENT '现场一卡通介质 UUID，未发卡时为空',
  card_bound_at TIMESTAMP(6) NULL COMMENT '数据库记录的本次发卡或绑卡 UTC 时间',
  card_released_at TIMESTAMP(6) NULL COMMENT '数据库记录的本次回收或解绑 UTC 时间',
  active_card_id BINARY(16) GENERATED ALWAYS AS (IF(card_released_at IS NULL, card_id, NULL)) STORED COMMENT '尚未解绑的卡片 UUID，用于阻止一卡多车',
  waybill_no VARCHAR(64) NULL COMMENT '本次运输单号，未提供可空',
  status VARCHAR(24) NOT NULL COMMENT '车次生命周期状态',
  queue_stage VARCHAR(24) NULL COMMENT '工作台主队列节点，结束后为空',
  stage_started_at TIMESTAMP(6) NULL COMMENT '数据库生成的当前排队阶段 UTC 起点',
  arrived_at TIMESTAMP(6) NULL COMMENT '数据库记录的实际到场 UTC 时间',
  unload_disposition VARCHAR(24) NULL COMMENT '监卸结论，空表示未确认或模板未启用',
  unload_deduction_kg DECIMAL(18,4) NULL COMMENT '现场异常扣重，千克，空表示未记录',
  unload_evidence_id BINARY(16) NULL COMMENT '监卸或退车证据 UUID',
  unload_confirmed_by BINARY(16) NULL COMMENT '可信监卸确认账号 UUID',
  unload_confirmed_at TIMESTAMP(6) NULL COMMENT '数据库生成的监卸确认 UTC 时间',
  row_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始，每次变更递增',
  created_by BINARY(16) NULL COMMENT '可信创建账号 UUID，系统执行无账号时为空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID，系统执行无账号时为空',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间',
  PRIMARY KEY (tenant_id, id),
  UNIQUE KEY uk_bw_exec_task_no (tenant_id, task_no),
  UNIQUE KEY uk_bw_exec_task_card (tenant_id, active_card_id),
  UNIQUE KEY uk_bw_exec_task_notice (tenant_id, id, notice_id),
  KEY idx_bw_exec_notice_tasks (tenant_id, notice_id, created_at, id),
  KEY idx_bw_exec_queue (tenant_id, queue_stage, stage_started_at, id),
  CONSTRAINT fk_bw_exec_task_notice FOREIGN KEY (tenant_id, notice_id)
    REFERENCES bw_inbound_notice (tenant_id, id),
  CONSTRAINT fk_bw_exec_task_template FOREIGN KEY (tenant_id, template_id)
    REFERENCES bw_commodity_template (tenant_id, id),
  CHECK (unload_deduction_kg IS NULL OR unload_deduction_kg >= 0),
  CHECK ((vehicle_identity_type = 'PLATED' AND vehicle_plate IS NOT NULL AND CHAR_LENGTH(TRIM(vehicle_plate)) > 0 AND temporary_vehicle_no IS NULL)
    OR (vehicle_identity_type = 'UNPLATED' AND vehicle_plate IS NULL AND temporary_vehicle_no IS NOT NULL AND CHAR_LENGTH(TRIM(temporary_vehicle_no)) > 0)
    OR (vehicle_identity_type = 'OTHER' AND vehicle_plate IS NULL AND temporary_vehicle_no IS NULL)),
  CHECK ((card_id IS NULL AND card_bound_at IS NULL AND card_released_at IS NULL)
    OR (card_id IS NOT NULL AND card_bound_at IS NOT NULL AND (card_released_at IS NULL OR card_released_at >= card_bound_at)))
) ENGINE=InnoDB COMMENT='入库作业单';

CREATE TABLE bw_quality_order (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
  id BINARY(16) NOT NULL COMMENT '入库质检单版本 UUID',
  task_id BINARY(16) NOT NULL COMMENT '所属作业单 UUID',
  quality_no VARCHAR(64) NOT NULL COMMENT '质检版本单据编码，复检版本独立发号',
  version INT UNSIGNED NOT NULL COMMENT '作业内质检版本，从 1 递增',
  is_active TINYINT NOT NULL COMMENT '是否现行，1 为现行，0 为历史',
  active_task_id BINARY(16) GENERATED ALWAYS AS (IF(is_active = 1, task_id, NULL)) STORED COMMENT '用于约束每作业最多一个现行版本',
  sample_barcode VARCHAR(64) NULL COMMENT '租户内盲检样品条码，未采样时为空',
  sampled_at TIMESTAMP(6) NULL COMMENT '数据库记录的采样登记 UTC 时间',
  status VARCHAR(24) NOT NULL COMMENT '质检处理状态',
  decision VARCHAR(24) NULL COMMENT '质检结论，尚无结论时为空',
  result_snapshot JSON NULL COMMENT '实际指标、单位、检验标准版本、外观及质保书证据引用，未出结果为空',
  row_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始，每次变更递增',
  created_by BINARY(16) NULL COMMENT '可信创建账号 UUID，系统执行无账号时为空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID，系统执行无账号时为空',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间',
  PRIMARY KEY (tenant_id, id),
  UNIQUE KEY uk_bw_exec_quality_no (tenant_id, quality_no),
  UNIQUE KEY uk_bw_exec_quality_version (tenant_id, task_id, version),
  UNIQUE KEY uk_bw_exec_quality_active (tenant_id, active_task_id),
  UNIQUE KEY uk_bw_exec_quality_sample (tenant_id, sample_barcode),
  UNIQUE KEY uk_bw_exec_quality_task_id (tenant_id, task_id, id),
  KEY idx_bw_exec_quality_status (tenant_id, status, created_at, id),
  CONSTRAINT fk_bw_exec_quality_task FOREIGN KEY (tenant_id, task_id)
    REFERENCES bw_inbound_task (tenant_id, id),
  CHECK (version > 0 AND is_active IN (0, 1))
) ENGINE=InnoDB COMMENT='入库质检单版本';

CREATE TABLE bw_metering_order (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
  id BINARY(16) NOT NULL COMMENT '入库计量单 UUID',
  task_id BINARY(16) NOT NULL COMMENT '所属作业单 UUID',
  metering_no VARCHAR(64) NOT NULL COMMENT '租户内计量单编码',
  status VARCHAR(24) NOT NULL COMMENT '计量单独立状态',
  method VARCHAR(24) NOT NULL COMMENT '地磅差重、外部净重或理计抄码方式',
  gross_kg DECIMAL(18,4) NULL COMMENT '毛重，千克，未过毛或不适用为空',
  tare_kg DECIMAL(18,4) NULL COMMENT '离场原始皮重，千克，退车时可等于或大于毛重',
  actual_net_kg DECIMAL(18,4) NULL COMMENT '实收物理净重，千克，无实测或退车时为空',
  theoretical_kg DECIMAL(18,4) NULL COMMENT '理计重量，千克，未采集时为空',
  package_count INT UNSIGNED NULL COMMENT '件或捆数，0 不得用于已确认件货',
  variance_ratio DECIMAL(30,12) NULL COMMENT '有符号磅差比值，0.01 表示高 1%，差值除以理重',
  tare_gate_quality_id BINARY(16) NULL COMMENT '过皮时采用的质检版本 UUID，无质检前置要求时为空',
  measurement_evidence JSON NULL COMMENT '秤台稳定读数、采集时点、外部票据或抄码分摊及容差版本快照',
  row_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始，每次变更递增',
  created_by BINARY(16) NULL COMMENT '可信创建账号 UUID，系统执行无账号时为空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID，系统执行无账号时为空',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间',
  PRIMARY KEY (tenant_id, id),
  UNIQUE KEY uk_bw_exec_meter_no (tenant_id, metering_no),
  UNIQUE KEY uk_bw_exec_meter_task (tenant_id, task_id),
  UNIQUE KEY uk_bw_exec_meter_task_id (tenant_id, task_id, id),
  KEY idx_bw_exec_meter_status (tenant_id, status, created_at, id),
  CONSTRAINT fk_bw_exec_meter_task FOREIGN KEY (tenant_id, task_id)
    REFERENCES bw_inbound_task (tenant_id, id),
  CONSTRAINT fk_bw_exec_meter_quality FOREIGN KEY (tenant_id, task_id, tare_gate_quality_id)
    REFERENCES bw_quality_order (tenant_id, task_id, id),
  CHECK (gross_kg IS NULL OR gross_kg > 0),
  CHECK (tare_kg IS NULL OR (gross_kg IS NOT NULL AND tare_kg > 0)),
  CHECK (actual_net_kg IS NULL OR actual_net_kg > 0),
  CHECK (theoretical_kg IS NULL OR theoretical_kg > 0),
  CHECK (package_count IS NULL OR package_count > 0)
) ENGINE=InnoDB COMMENT='入库计量单';

CREATE TABLE bw_receipt_order (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
  id BINARY(16) NOT NULL COMMENT '入库单 UUID',
  task_id BINARY(16) NOT NULL COMMENT '所属作业单 UUID',
  notice_id BINARY(16) NOT NULL COMMENT '来源通知 UUID，与作业单一致',
  receipt_no VARCHAR(64) NOT NULL COMMENT '租户内入库单编码',
  status VARCHAR(24) NOT NULL COMMENT '入库单状态：DRAFT 草稿、POSTED 已过账、VOIDED 已作废、REVERSED 已冲红',
  active_task_id BINARY(16) GENERATED ALWAYS AS (
    CASE WHEN status IN ('VOIDED', 'REVERSED') THEN NULL
         WHEN status IN ('DRAFT', 'POSTED') THEN task_id
         ELSE NULL END
  ) STORED COMMENT '草稿和已过账占用作业槽位，作废和冲红均释放槽位',
  replaces_receipt_id BINARY(16) NULL COMMENT '冲红重开所替代的同一作业原入库单 UUID',
  quality_state VARCHAR(24) NOT NULL COMMENT '本次入账质量状态，区分待化验和已有结论',
  metering_id BINARY(16) NOT NULL COMMENT '本次采用的同作业计量单 UUID',
  quality_id BINARY(16) NULL COMMENT '本次采用的同作业质检版本 UUID，模板无需质检或草稿待绑定时为空',
  physical_net_kg DECIMAL(18,4) NULL COMMENT '实测物理净重，千克，仅有理计且无实测时为空',
  deduction_kg DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '影响入库账的扣重总量，千克，默认零',
  accounting_weight_kg DECIMAL(18,4) NOT NULL COMMENT '按选定计量基准减扣重所得入库账面重量，千克',
  posting_snapshot JSON NOT NULL COMMENT '固定采用的质量值、计量口径、扣重公式版本、输入输出及单位换算',
  posted_at TIMESTAMP(6) NULL COMMENT '数据库生成的库存过账 UTC 时间，未过账为空',
  row_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，从 0 开始，每次变更递增',
  created_by BINARY(16) NULL COMMENT '可信创建账号 UUID，系统执行无账号时为空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID，系统执行无账号时为空',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间',
  PRIMARY KEY (tenant_id, id),
  UNIQUE KEY uk_bw_exec_receipt_no (tenant_id, receipt_no),
  UNIQUE KEY uk_bw_exec_receipt_active (tenant_id, active_task_id),
  UNIQUE KEY uk_bw_exec_receipt_task_id (tenant_id, task_id, id),
  UNIQUE KEY uk_bw_exec_receipt_notice_id (tenant_id, notice_id, id),
  KEY idx_bw_exec_receipt_status (tenant_id, status, created_at, id),
  CONSTRAINT fk_bw_exec_receipt_task FOREIGN KEY (tenant_id, task_id, notice_id)
    REFERENCES bw_inbound_task (tenant_id, id, notice_id),
  CONSTRAINT fk_bw_exec_receipt_meter FOREIGN KEY (tenant_id, task_id, metering_id)
    REFERENCES bw_metering_order (tenant_id, task_id, id),
  CONSTRAINT fk_bw_exec_receipt_quality FOREIGN KEY (tenant_id, task_id, quality_id)
    REFERENCES bw_quality_order (tenant_id, task_id, id),
  CONSTRAINT fk_bw_exec_receipt_replaces FOREIGN KEY (tenant_id, task_id, replaces_receipt_id)
    REFERENCES bw_receipt_order (tenant_id, task_id, id),
  CHECK (status IN ('DRAFT', 'POSTED', 'VOIDED', 'REVERSED')),
  CHECK (replaces_receipt_id IS NULL OR replaces_receipt_id <> id),
  CHECK (accounting_weight_kg >= 0 AND deduction_kg >= 0 AND (physical_net_kg IS NULL OR physical_net_kg > 0))
) ENGINE=InnoDB COMMENT='入库单';

CREATE TABLE bw_receipt_order_line (
  tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
  id BINARY(16) NOT NULL COMMENT '入库单明细 UUID',
  receipt_order_id BINARY(16) NOT NULL COMMENT '所属入库单 UUID',
  notice_id BINARY(16) NOT NULL COMMENT '来源入仓通知 UUID',
  notice_line_id BINARY(16) NOT NULL COMMENT '同一通知的来源明细 UUID',
  location_id BINARY(16) NOT NULL COMMENT '主数据库位 UUID',
  batch_no VARCHAR(64) NOT NULL COMMENT '库内批次号',
  lpn VARCHAR(64) NULL COMMENT '钢卷或捆包标识，散货为空',
  heat_no VARCHAR(64) NULL COMMENT '钢厂炉批号，散货或未录入时为空',
  unit_id BINARY(16) NOT NULL COMMENT '入账采用的库存单位 UUID',
  inventory_quantity DECIMAL(30,12) NOT NULL COMMENT '已分摊的库存基础单位入库量',
  accounting_weight_kg DECIMAL(18,4) NOT NULL COMMENT '本行分摊的账面入库重量，千克',
  item_snapshot JSON NOT NULL COMMENT '固定的商品身份、规格、材质、计量换算及批次字段值',
  measurement_snapshot JSON NULL COMMENT '本行件数、实重或理重及分摊依据，散货可空',
  created_by BINARY(16) NULL COMMENT '可信创建账号 UUID，系统执行无账号时为空',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
  updated_by BINARY(16) NULL COMMENT '可信最后修改账号 UUID，系统执行无账号时为空',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最后修改时间',
  PRIMARY KEY (tenant_id, id),
  UNIQUE KEY uk_bw_exec_receipt_lpn (tenant_id, receipt_order_id, lpn),
  KEY idx_bw_exec_receipt_lines (tenant_id, receipt_order_id, id),
  KEY idx_bw_exec_receipt_batch (tenant_id, batch_no, id),
  KEY idx_bw_exec_lpn_lookup (tenant_id, lpn, id),
  CONSTRAINT fk_bw_exec_line_order FOREIGN KEY (tenant_id, notice_id, receipt_order_id)
    REFERENCES bw_receipt_order (tenant_id, notice_id, id),
  CONSTRAINT fk_bw_exec_line_notice FOREIGN KEY (tenant_id, notice_id, notice_line_id)
    REFERENCES bw_inbound_notice_line (tenant_id, notice_id, id),
  CHECK (inventory_quantity > 0 AND accounting_weight_kg >= 0)
) ENGINE=InnoDB COMMENT='入库单明细';
