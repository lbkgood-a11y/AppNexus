-- 授权版本与主体状态同表原子推进；后续缓存必须比较该版本，不能只依赖固定 TTL。
ALTER TABLE identity_account ADD authority_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_member ADD authority_version BIGINT NOT NULL DEFAULT 0;
