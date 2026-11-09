-- removed_at 区分“暂时禁用”和“已移除”；保留成员 ID 与历史引用，重新加入时复用原记录。
ALTER TABLE tenant_member
    ADD COLUMN removed_at TIMESTAMP(6) NULL AFTER enabled,
    ADD INDEX ix_tenant_member_status (tenant_id, enabled, removed_at);
