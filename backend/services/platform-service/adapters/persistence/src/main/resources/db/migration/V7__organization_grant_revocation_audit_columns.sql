-- 已发布的 V6 不可修改；此迁移为撤权审计增加操作者字段。
ALTER TABLE organization_capability_grant ADD COLUMN revoked_by BINARY(16) NULL;
