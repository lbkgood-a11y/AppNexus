-- 系统预置没有实际账号，允许 NULL；不伪造零 UUID 或把迁移执行人当业务操作者。
ALTER TABLE tenant_dictionary MODIFY created_by BINARY(16) NULL COMMENT '实际创建账号；系统预置或历史未知为空', MODIFY updated_by BINARY(16) NULL COMMENT '实际修改账号；系统执行或历史未知为空';
ALTER TABLE tenant_dictionary_item MODIFY created_by BINARY(16) NULL COMMENT '实际创建账号；系统预置或历史未知为空', MODIFY updated_by BINARY(16) NULL COMMENT '实际修改账号；系统执行或历史未知为空';
