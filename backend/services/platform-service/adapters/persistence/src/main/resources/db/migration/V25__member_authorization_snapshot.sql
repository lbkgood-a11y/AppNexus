-- 成员角色关系保留为归属索引；独立授权快照固定当次操作与范围，角色增权不自动扩展历史授权。
CREATE TABLE access_member_authorization (
 tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID，必填，不接受跨租户引用',
 member_id BINARY(16) NOT NULL COMMENT '租户成员 UUID，必填，不是全局账号 UUID',
 role_id BINARY(16) NOT NULL COMMENT '同租户角色 UUID，必填',
 role_version BIGINT NOT NULL COMMENT '生成快照时的角色版本，角色后续增权不更新本值',
 expires_at TIMESTAMP(6) NULL COMMENT '授权到期 UTC 时间，缺省 NULL 表示无固定到期时间，撤权和主体状态仍须校验',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '首次授权时间，缺省数据库当前 UTC 时间',
 PRIMARY KEY(tenant_id,member_id,role_id),
 CONSTRAINT fk_member_authorization_assignment FOREIGN KEY(tenant_id,member_id,role_id) REFERENCES access_member_role(tenant_id,member_id,role_id) ON DELETE CASCADE
);

-- 角色权限不是本表外键：角色减权后保留历史授权边界，但运行时必须再与当前角色权限求交。
CREATE TABLE access_member_authorization_permission (
 tenant_id BINARY(16) NOT NULL COMMENT '快照所属租户 UUID，必填',
 member_id BINARY(16) NOT NULL COMMENT '快照所属成员 UUID，必填',
 role_id BINARY(16) NOT NULL COMMENT '授权分支角色 UUID，必填',
 permission_id BINARY(16) NOT NULL COMMENT '已审核的操作权限 UUID，必填',
 scope_type VARCHAR(32) NULL COMMENT '数据范围类型，无范围要求时 NULL；组织资源禁止配置 SELF',
 resource_type VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '领域声明资源标识，无数据策略时 NULL，禁止 SQL 或表名',
 PRIMARY KEY(tenant_id,member_id,role_id,permission_id),
 CONSTRAINT fk_member_authorization_permission_grant FOREIGN KEY(tenant_id,member_id,role_id) REFERENCES access_member_authorization(tenant_id,member_id,role_id) ON DELETE CASCADE,
 CONSTRAINT fk_member_authorization_permission_catalog FOREIGN KEY(permission_id) REFERENCES access_permission(id),
 CONSTRAINT ck_member_authorization_scope CHECK((scope_type IS NULL AND resource_type IS NULL) OR (scope_type IN('SELF','ORGANIZATION','ORGANIZATION_SUBTREE','ALL') AND resource_type IS NOT NULL))
);

-- 范围值必须与授权分支及租户组织同时匹配，防止跨分支拼接与跨租户引用。
CREATE TABLE access_member_authorization_organization (
 tenant_id BINARY(16) NOT NULL COMMENT '组织及授权共同租户 UUID，必填',
 member_id BINARY(16) NOT NULL COMMENT '授权成员 UUID，必填',
 role_id BINARY(16) NOT NULL COMMENT '授权角色 UUID，必填',
 permission_id BINARY(16) NOT NULL COMMENT '范围所属操作 UUID，必填',
 organization_id BINARY(16) NOT NULL COMMENT '本租户组织 UUID，必填，不得使用外租户节点',
 PRIMARY KEY(tenant_id,member_id,role_id,permission_id,organization_id),
 CONSTRAINT fk_member_authorization_org_policy FOREIGN KEY(tenant_id,member_id,role_id,permission_id) REFERENCES access_member_authorization_permission(tenant_id,member_id,role_id,permission_id) ON DELETE CASCADE,
 CONSTRAINT fk_member_authorization_org_unit FOREIGN KEY(tenant_id,organization_id) REFERENCES tenant_organization_unit(tenant_id,id)
);

-- 一次性保留迁移前授权效果；后续角色变更不得重新执行这些复制语句。
INSERT INTO access_member_authorization(tenant_id,member_id,role_id,role_version)
 SELECT mr.tenant_id,mr.member_id,mr.role_id,r.version FROM access_member_role mr JOIN access_role r ON r.id=mr.role_id AND r.tenant_id=mr.tenant_id;
INSERT INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id,scope_type,resource_type)
 SELECT mr.tenant_id,mr.member_id,mr.role_id,rp.permission_id,dp.scope_type,dp.resource_type FROM access_member_role mr JOIN access_role_permission rp ON rp.role_id=mr.role_id LEFT JOIN access_role_data_policy dp ON dp.role_id=rp.role_id AND dp.permission_id=rp.permission_id;
INSERT INTO access_member_authorization_organization(tenant_id,member_id,role_id,permission_id,organization_id)
 SELECT mr.tenant_id,mr.member_id,mr.role_id,o.permission_id,o.organization_id FROM access_member_role mr JOIN access_role_data_policy_organization o ON o.tenant_id=mr.tenant_id AND o.role_id=mr.role_id;

-- 原 SELF 对组织资源无执行语义，只从可配置元数据移除，保留历史快照供解释；创建组织当前仅支持 ALL。
DELETE st FROM access_permission_scope_type st JOIN access_permission p ON p.id=st.permission_id
 WHERE p.resource='organization.unit' AND NOT EXISTS(SELECT 1 FROM access_role_data_policy dp WHERE dp.permission_id=st.permission_id AND dp.scope_type=st.scope_type)
 AND (st.scope_type='SELF' OR (p.action='CREATE' AND st.scope_type<>'ALL'));
