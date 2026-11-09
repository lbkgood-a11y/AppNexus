-- 权限数据范围元数据由受控发布维护；未登记的权限不得保存数据策略。
CREATE TABLE access_permission_scope_type (
 permission_id BINARY(16) NOT NULL,
 scope_type VARCHAR(32) NOT NULL,
 resource_type VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(permission_id,scope_type),
 CONSTRAINT fk_permission_scope_type_permission FOREIGN KEY(permission_id) REFERENCES access_permission(id),
 CONSTRAINT ck_permission_scope_type CHECK(scope_type IN('SELF','ORGANIZATION','ORGANIZATION_SUBTREE','ALL'))
);

-- 每个角色权限只能选择一种范围类型；策略必须依附已授予的角色权限，撤销功能权限时由外键级联删除。
CREATE TABLE access_role_data_policy (
 role_id BINARY(16) NOT NULL,
 permission_id BINARY(16) NOT NULL,
 scope_type VARCHAR(32) NOT NULL,
 resource_type VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 configured_by BINARY(16) NOT NULL,
 configured_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(role_id,permission_id),
 CONSTRAINT fk_role_data_policy_grant FOREIGN KEY(role_id,permission_id) REFERENCES access_role_permission(role_id,permission_id) ON DELETE CASCADE,
 CONSTRAINT fk_role_data_policy_supported FOREIGN KEY(permission_id,scope_type) REFERENCES access_permission_scope_type(permission_id,scope_type),
 CONSTRAINT ck_role_data_policy_scope CHECK(scope_type IN('SELF','ORGANIZATION','ORGANIZATION_SUBTREE','ALL')),
 INDEX ix_role_data_policy_permission(permission_id,role_id)
);

-- 组织范围值使用独立行并携带 tenant_id，复合外键阻止策略引用其他租户组织。
CREATE TABLE access_role_data_policy_organization (
 role_id BINARY(16) NOT NULL,
 permission_id BINARY(16) NOT NULL,
 tenant_id BINARY(16) NOT NULL,
 organization_id BINARY(16) NOT NULL,
 PRIMARY KEY(role_id,permission_id,organization_id),
 CONSTRAINT fk_policy_organization_policy FOREIGN KEY(role_id,permission_id) REFERENCES access_role_data_policy(role_id,permission_id) ON DELETE CASCADE,
 CONSTRAINT fk_policy_organization_unit FOREIGN KEY(tenant_id,organization_id) REFERENCES tenant_organization_unit(tenant_id,id),
 INDEX ix_policy_organization_tenant(tenant_id,organization_id)
);

-- 组织读取与写入首批支持本人、指定组织、组织子树和当前租户全部；GRANT 不使用数据范围代替管理权。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,s.scope_type,'organization.unit' FROM access_permission
JOIN (
 SELECT 'SELF' scope_type UNION ALL SELECT 'ORGANIZATION' UNION ALL
 SELECT 'ORGANIZATION_SUBTREE' UNION ALL SELECT 'ALL'
) s
WHERE code IN('organization.unit:read','organization.unit:create','organization.unit:update','organization.unit:delete');
