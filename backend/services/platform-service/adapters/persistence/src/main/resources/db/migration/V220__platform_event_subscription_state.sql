-- 事件订阅开关独立于 Outbox 事实，缺省允许运行。
CREATE TABLE platform_event_subscription_state (
    id BINARY(16) NOT NULL PRIMARY KEY COMMENT '由事件类型和消费者生成的稳定订阅 UUID',
    event_type VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后端登记的事件类型',
    consumer_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后端登记的消费者标识',
    paused BOOLEAN NOT NULL DEFAULT FALSE COMMENT '暂停时仍创建投递，禁止新的领取',
    created_by BINARY(16) NOT NULL COMMENT '首次设置开关的可信账号',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '首次设置的数据库 UTC 时间',
    updated_by BINARY(16) NOT NULL COMMENT '最近设置开关的可信账号',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近设置的数据库 UTC 时间',
    UNIQUE KEY uk_event_subscription (event_type,consumer_id)
);

-- 新动作独立授权，由平台权限管理按角色显式分配。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0220-7000-8000-000000000001'),'PLATFORM','platform.task:subscription_state',
       'platform.task','subscription_state','管理事件订阅执行状态','Manage event subscription execution',
       '授权暂停和恢复已登记的业务事件订阅，操作必须填写原因。');
