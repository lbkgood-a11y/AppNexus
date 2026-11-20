-- 商品组织归属由业务明确选择；历史未知值保留NULL，不推断为创建人或当前成员组织。
ALTER TABLE md_product_v1
 ADD COLUMN organization_id BINARY(16) NULL COMMENT '商品归属组织UUID；来自可信租户启用组织，历史未知为NULL',
 ADD INDEX idx_product_organization_code (tenant_id,organization_id,code,id);

-- 新数据权限资源缺少策略默认拒绝；不把旧角色自动扩为ALL。已有角色须在授权中心显式配置。
