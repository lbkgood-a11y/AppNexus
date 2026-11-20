-- 组合授权沿用原角色和成员策略行、四字段审计及级联撤销，不另建业务私有授权表。
ALTER TABLE access_permission_scope_type DROP CHECK ck_permission_scope_type,
 ADD CONSTRAINT ck_permission_scope_type CHECK(scope_type IN('SELF','ORGANIZATION','ORGANIZATION_SUBTREE','ALL','RULES'));
ALTER TABLE access_role_data_policy DROP CHECK ck_role_data_policy_scope,
 ADD CONSTRAINT ck_role_data_policy_scope CHECK(scope_type IN('SELF','ORGANIZATION','ORGANIZATION_SUBTREE','ALL','RULES')),
 ADD COLUMN rules_json JSON NULL COMMENT '统一组合授权规则；仅RULES使用，历史NULL不改变原范围；组内记录与条件AND，组间OR';
ALTER TABLE access_member_authorization_permission DROP CHECK ck_member_authorization_scope,
 ADD CONSTRAINT ck_member_authorization_scope CHECK((scope_type IS NULL AND resource_type IS NULL) OR (scope_type IN('SELF','ORGANIZATION','ORGANIZATION_SUBTREE','ALL','RULES') AND resource_type IS NOT NULL)),
 ADD COLUMN rules_json JSON NULL COMMENT '成员自定义组合授权；继承时读取角色当前规则，历史NULL不扩权，恢复继承清除自定义规则';
-- 只有已实现的客商查询操作开放组合范围；新增、编辑、启停不因查看授权获得写权限。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
 SELECT id,'RULES','masterdata.partner' FROM access_permission WHERE scope_type='TENANT' AND code='masterdata.partner:read';
