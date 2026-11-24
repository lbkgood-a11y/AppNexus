-- 质量状态和指标以本笔来源覆盖库存批次。历史库存缺少事实时均保持 NULL。
ALTER TABLE bw_stock_receipt
  ADD COLUMN quality_state VARCHAR(24) NULL COMMENT '批次质量状态：PENDING_LAB 待化验、QUALIFIED 合格或 QUALITY_FROZEN 冻结',
  ADD COLUMN quality_version INT UNSIGNED NULL COMMENT '该作业已应用的质检版本，未判定或历史未知为空',
  ADD COLUMN quality_snapshot JSON NULL COMMENT '该批来源覆盖范围的实验室指标、单位和规则快照，未化验为空',
  MODIFY COLUMN pending_quantity DECIMAL(30,12) NOT NULL COMMENT '质量待检或冻结保留量，库存基础单位',
  ADD CONSTRAINT ck_bw_stock_quality_state CHECK (
    quality_state IS NULL OR (quality_state='PENDING_LAB' AND quality_version IS NULL AND quality_snapshot IS NULL)
    OR (quality_state IN ('QUALIFIED','QUALITY_FROZEN') AND quality_version IS NOT NULL AND quality_version>0
        AND quality_snapshot IS NOT NULL)
  );
