-- 现有角色保持 NULL，已有操作策略继续作为显式覆盖，无授权迁移或范围扩大。
ALTER TABLE access_role
    ADD COLUMN object_scope_defaults JSON NULL COMMENT '按业务对象主资源键保存的默认记录范围；NULL 表示没有默认范围';
