-- 历史草案，仅用于追溯早期验证。正式执行以 V203__platform_event_delivery.sql 为准。
-- 切换前停止旧发布方和 Worker，确认旧执行事务已退出。原表与回执保留以供对账。
ALTER TABLE platform_outbox
    ADD COLUMN event_revision SMALLINT NOT NULL DEFAULT 1 COMMENT '事件契约版本，0 表示存量旧信封';

UPDATE platform_outbox SET event_revision = 0;

CREATE TABLE platform_event_delivery (
    id BINARY(16) NOT NULL PRIMARY KEY COMMENT '投递 UUID，人工重试使用此标识',
    tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，平台事件使用零 UUID',
    event_id BINARY(16) NOT NULL COMMENT '对应不可变 Outbox 事件',
    consumer_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '代码声明的稳定消费者标识',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '待处理、处理中、已完成或待人工',
    attempts INT NOT NULL DEFAULT 0 COMMENT '本轮已领取次数，包含崩溃恢复',
    total_attempts INT NOT NULL DEFAULT 0 COMMENT '累计已知领取次数，人工重试不清零',
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '可领取 UTC 时间',
    lease_token VARCHAR(36) NULL COMMENT '当前领取的随机令牌，禁止通过管理接口返回',
    lease_until TIMESTAMP(6) NULL COMMENT '租约截止 UTC 时间',
    failure_code VARCHAR(40) NULL COMMENT '受控失败码，不存业务正文或异常原文',
    created_by BINARY(16) NULL COMMENT '可信创建账号，系统执行或历史未知为空',
    created_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建 UTC 时间，历史未知为空',
    updated_by BINARY(16) NULL COMMENT '可信修改账号，系统执行或历史未知为空',
    updated_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '修改 UTC 时间，历史未知为空',
    UNIQUE KEY uk_event_delivery (tenant_id,event_id,consumer_id),
    KEY ix_delivery_event (event_id),
    KEY ix_delivery_claim (status,available_at,lease_until),
    CONSTRAINT fk_delivery_event FOREIGN KEY (event_id) REFERENCES platform_outbox(id)
);

CREATE TABLE platform_event_delivery_attempt (
    delivery_id BINARY(16) NOT NULL COMMENT '所属投递 UUID',
    attempt_no INT NOT NULL COMMENT '投递累计领取次数，作为本次执行序号',
    outcome VARCHAR(40) NOT NULL COMMENT '执行结果或在途状态，历史未知不伪造',
    failure_code VARCHAR(40) NULL COMMENT '受控失败码',
    created_by BINARY(16) NULL COMMENT '可信创建账号，系统执行为空',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '领取 UTC 时间',
    updated_by BINARY(16) NULL COMMENT '可信修改账号，系统执行为空',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '结果更新时间，UTC',
    PRIMARY KEY (delivery_id,attempt_no),
    CONSTRAINT fk_delivery_attempt FOREIGN KEY (delivery_id) REFERENCES platform_event_delivery(id)
);

-- 回填只认原消费者的回执。缺回执的成功记录转对账，避免再次触发业务副作用。
INSERT INTO platform_event_delivery
    (id,tenant_id,event_id,consumer_id,status,attempts,total_attempts,available_at,failure_code,
     created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN(UUID()),o.tenant_id,o.id,o.consumer_id,
       CASE
           WHEN o.consumer_id = 'legacy-unmapped' THEN 'MANUAL'
           WHEN c.event_id IS NOT NULL THEN 'DONE'
           WHEN o.status IN ('MANUAL','DONE') OR o.attempts >= 5 THEN 'MANUAL'
           ELSE 'PENDING'
       END,
       o.attempts,o.attempts,o.available_at,
       CASE
           WHEN o.consumer_id = 'legacy-unmapped' THEN 'LEGACY_UNMAPPED'
           WHEN c.event_id IS NOT NULL THEN NULL
           WHEN o.status = 'DONE' THEN 'LEGACY_RECEIPT_MISSING'
           WHEN o.attempts >= 5 AND o.status <> 'MANUAL' THEN 'ATTEMPTS_EXHAUSTED'
           ELSE o.failure_code
       END,
       o.created_by,o.created_at,o.updated_by,o.updated_at
FROM (
    SELECT platform_outbox.*,
           CASE
               WHEN event_type IN ('AUTHORITY_INVALIDATED','INTEGRATION_AUTHORITY_CHANGED') THEN 'authority-invalidation'
               WHEN event_type IN ('INBOUND_RECEIPT_POSTED','INBOUND_QUALITY_DECIDED') THEN 'inbound-batch-quality'
               ELSE 'legacy-unmapped'
           END AS consumer_id
    FROM platform_outbox
) o
LEFT JOIN platform_event_consumption c
    ON c.tenant_id = o.tenant_id AND c.event_id = o.id AND BINARY c.consumer = BINARY o.consumer_id;
