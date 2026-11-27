-- 策略模板原 owner_id 已存储真实租户 UUID 或平台零 UUID，只统一列名。
ALTER TABLE access_policy_template RENAME COLUMN owner_id TO tenant_id;
ALTER TABLE access_policy_template MODIFY COLUMN tenant_id BINARY(16) NOT NULL
    COMMENT '模板所属租户 UUID，平台模板沿用零 UUID';

-- 角色衍生记录沿用父角色的租户，平台行使用 NULL。持久化写入从父角色读取归属。
ALTER TABLE access_role ADD UNIQUE KEY uk_access_role_id_owner (id, scope_owner_id);

ALTER TABLE access_role_permission ADD COLUMN tenant_id BINARY(16) NULL COMMENT '所属角色的租户 UUID，平台角色为空';
UPDATE access_role_permission child JOIN access_role parent ON parent.id = child.role_id
    SET child.tenant_id = parent.tenant_id;
ALTER TABLE access_role_permission
    ADD COLUMN role_scope_owner_id BINARY(16) GENERATED ALWAYS AS
        (COALESCE(tenant_id, 0x00000000000000000000000000000000)) STORED
        COMMENT '角色归属完整性校验键，平台域以零 UUID 表示',
    ADD CONSTRAINT fk_grant_role_scope_owner FOREIGN KEY (role_id, role_scope_owner_id)
        REFERENCES access_role(id, scope_owner_id);

ALTER TABLE access_role_data_policy ADD COLUMN tenant_id BINARY(16) NULL COMMENT '所属角色的租户 UUID，平台角色为空';
UPDATE access_role_data_policy child JOIN access_role parent ON parent.id = child.role_id
    SET child.tenant_id = parent.tenant_id;
ALTER TABLE access_role_data_policy
    ADD COLUMN role_scope_owner_id BINARY(16) GENERATED ALWAYS AS
        (COALESCE(tenant_id, 0x00000000000000000000000000000000)) STORED
        COMMENT '角色归属完整性校验键，平台域以零 UUID 表示',
    ADD CONSTRAINT fk_policy_role_scope_owner FOREIGN KEY (role_id, role_scope_owner_id)
        REFERENCES access_role(id, scope_owner_id);

ALTER TABLE access_role_field_security ADD COLUMN tenant_id BINARY(16) NULL COMMENT '所属角色的租户 UUID，平台角色为空';
UPDATE access_role_field_security child JOIN access_role parent ON parent.id = child.role_id
    SET child.tenant_id = parent.tenant_id;
ALTER TABLE access_role_field_security
    ADD COLUMN role_scope_owner_id BINARY(16) GENERATED ALWAYS AS
        (COALESCE(tenant_id, 0x00000000000000000000000000000000)) STORED
        COMMENT '角色归属完整性校验键，平台域以零 UUID 表示',
    ADD CONSTRAINT fk_field_role_scope_owner FOREIGN KEY (role_id, role_scope_owner_id)
        REFERENCES access_role(id, scope_owner_id);

-- 投递尝试与父投递共享同一信封范围，平台投递沿用零 UUID。
ALTER TABLE platform_event_delivery ADD UNIQUE KEY uk_delivery_id_tenant (id, tenant_id);
ALTER TABLE platform_event_delivery_attempt ADD COLUMN tenant_id BINARY(16) NULL
    COMMENT '所属投递的租户 UUID，平台投递沿用零 UUID';
UPDATE platform_event_delivery_attempt child JOIN platform_event_delivery parent ON parent.id = child.delivery_id
    SET child.tenant_id = parent.tenant_id;
ALTER TABLE platform_event_delivery_attempt
    MODIFY COLUMN tenant_id BINARY(16) NOT NULL COMMENT '所属投递的租户 UUID，平台投递沿用零 UUID',
    ADD CONSTRAINT fk_attempt_delivery_scope FOREIGN KEY (delivery_id, tenant_id)
        REFERENCES platform_event_delivery(id, tenant_id);

-- 集成审计保存客户端当时的归属；历史归属无法追溯时保持 NULL，审计不阻止客户端退役。
ALTER TABLE integration_client_audit ADD COLUMN tenant_id BINARY(16) NULL
    COMMENT '所属集成客户端的租户 UUID，历史归属无法追溯时为空';
UPDATE integration_client_audit child JOIN integration_client parent ON parent.id = child.client_id
    SET child.tenant_id = parent.tenant_id;
