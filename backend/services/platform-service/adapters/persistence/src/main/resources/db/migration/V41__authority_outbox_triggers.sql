-- 权限失效事件由应用事务显式发布，不创建需要 SUPER 权限的触发器。
-- 追加消费查询索引，租户与事件仍使用复合主键进行幂等判断。
CREATE INDEX ix_event_consumption_event ON platform_event_consumption(event_id,consumer);
