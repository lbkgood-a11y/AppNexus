-- PoC 使用独立测试库。保留事件、令牌族及消费者幂等键，统一记录主键列名。
ALTER TABLE outbox_event ADD UNIQUE KEY uk_poc_outbox_event (event_id);
ALTER TABLE outbox_event DROP PRIMARY KEY,
    ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库生成的记录主键',
    ADD PRIMARY KEY (id);

ALTER TABLE refresh_token_family ADD UNIQUE KEY uk_poc_refresh_family (family_id);
ALTER TABLE refresh_token_family DROP PRIMARY KEY,
    ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库生成的记录主键',
    ADD PRIMARY KEY (id);

ALTER TABLE event_consumption ADD UNIQUE KEY uk_poc_event_handler (event_id, handler_id);
ALTER TABLE event_consumption DROP PRIMARY KEY,
    ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库生成的记录主键',
    ADD PRIMARY KEY (id),
    ADD COLUMN tenant_id CHAR(36) NULL COMMENT '事件所属租户，继承事件范围，平台事件为空';
UPDATE event_consumption receipt JOIN outbox_event event ON event.event_id = receipt.event_id
    SET receipt.tenant_id = event.tenant_id;
