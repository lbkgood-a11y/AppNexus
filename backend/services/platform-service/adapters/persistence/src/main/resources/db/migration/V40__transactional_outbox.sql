-- 平台与租户事件共用 Outbox，平台事件 tenant_id 为零 UUID；租户事件必须由可信上下文指定。
CREATE TABLE platform_outbox (
    -- 不可变事件 UUID，同时作为消费幂等键。
    id BINARY(16) NOT NULL PRIMARY KEY,
    -- 可信租户 UUID；零 UUID 仅供平台级安全失效事件。
    tenant_id BINARY(16) NOT NULL,
    -- 受控事件类型，不接受调用方脚本、SQL 或任意类名。
    event_type VARCHAR(100) NOT NULL,
    -- 聚合标识，不存密码、Token、恢复码或敏感原值。
    aggregate_id VARCHAR(100) NOT NULL,
    -- PENDING/PROCESSING/DONE/MANUAL；MANUAL 必须人工处理，不能静默丢弃。
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- 已领取次数，默认零，包含崩溃后的重新领取。
    attempts INT NOT NULL DEFAULT 0,
    -- 下一次可领取 UTC 时间，初始立即执行；重试退避由服务端计算。
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- 本次领取的随机令牌，防止过期 Worker 完成新实例的租约。
    lease_token VARCHAR(36) NULL,
    -- 租约截止 UTC 时间，领取后固定 30 秒；崩溃到期可恢复。
    lease_until TIMESTAMP(6) NULL,
    -- 受控错误分类，不保存异常正文或 SQL。
    failure_code VARCHAR(40) NULL,
    -- 事件创建时间，UTC，和业务事务一并提交。
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_outbox_claim (status,available_at,lease_until),
    INDEX ix_outbox_tenant (tenant_id,id)
);
CREATE TABLE platform_event_consumption (
    -- 可信消费租户边界，不能由事件 payload 任意覆盖。
    tenant_id BINARY(16) NOT NULL,
    -- 已执行事件 UUID，与消费者共同形成幂等键。
    event_id BINARY(16) NOT NULL,
    -- 固定消费者名称，不能使用用户输入作为执行类名。
    consumer VARCHAR(100) NOT NULL,
    -- 事务提交时间，UTC；与消费副作用在同事务中记录。
    consumed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tenant_id,event_id,consumer)
);
