-- 保留存量成员分支的逐操作策略和原继承模式；新增列缺省无对象默认范围。
ALTER TABLE access_member_authorization
    ADD COLUMN object_scope_defaults JSON NULL COMMENT '成员自定义分支按对象主资源键保存的默认范围；NULL 表示未配置';
