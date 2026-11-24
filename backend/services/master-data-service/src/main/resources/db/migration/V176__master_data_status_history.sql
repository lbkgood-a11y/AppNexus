-- 主数据独立启停历史。商品及其规格、SKU、组件继续由商品聚合记录快照。
-- 写入由已授权的状态 CAS 成功后追加，与原 UPDATE 使用同一个事务。
CREATE TABLE md_master_status_change (
    tenant_id BINARY(16) NOT NULL COMMENT '归属租户 UUID；记录只可在同一可信租户读取',
    object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务对象或其子资源的固定编码',
    object_id BINARY(16) NOT NULL COMMENT '发生启停的主数据记录 UUID',
    to_version BIGINT NOT NULL COMMENT '成功启停后产生的记录版本',
    before_enabled BOOLEAN NOT NULL COMMENT '变更前启用状态',
    after_enabled BOOLEAN NOT NULL COMMENT '变更后启用状态',
    created_by BINARY(16) NULL COMMENT '执行状态变更的可信账号 UUID；未知时为 NULL',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的变更时间，UTC，精度微秒',
    updated_by BINARY(16) NULL COMMENT '不可变历史的最后修改账号，首次填充后不更新',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '不可变历史的最后修改时间，UTC，精度微秒',
    PRIMARY KEY (tenant_id, object_type, object_id, to_version),
    CHECK (before_enabled <> after_enabled)
) COMMENT='主数据记录的启停变更历史';
