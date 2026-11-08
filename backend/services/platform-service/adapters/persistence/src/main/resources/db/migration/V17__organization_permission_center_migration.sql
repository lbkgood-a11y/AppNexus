-- 权限目录由权限中心统一持有；组织域只消费稳定权限编码，不再解释成员直授权表。
CREATE TABLE access_permission (
 code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 resource_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
);

-- 兼容迁移阶段保留成员级有效期；后续角色权限和成员角色均以同一权限目录为权威来源。
CREATE TABLE access_member_permission (
 tenant_id BINARY(16) NOT NULL,
 member_id BINARY(16) NOT NULL,
 permission_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expires_at TIMESTAMP(6) NULL,
 granted_by BINARY(16) NOT NULL,
 granted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,member_id,permission_code),
 CONSTRAINT fk_access_member_permission_member FOREIGN KEY(tenant_id,member_id) REFERENCES tenant_member(tenant_id,id),
 CONSTRAINT fk_access_member_permission_catalog FOREIGN KEY(permission_code) REFERENCES access_permission(code)
);

INSERT INTO access_permission(code,name,resource_code,operation_code) VALUES
 ('organization.read','查看组织','organization','READ'),
 ('organization.create','创建组织','organization','CREATE'),
 ('organization.update','修改组织','organization','UPDATE'),
 ('organization.delete','删除组织','organization','DELETE'),
 ('organization.grant','管理组织权限','organization','GRANT');

-- 将仍有效的旧组织直授权一次性迁入权限中心，旧表之后只承担迁移审计兼容。
INSERT INTO access_member_permission(tenant_id,member_id,permission_code,expires_at,granted_by,granted_at)
SELECT tenant_id,member_id,CONCAT('organization.',LOWER(capability)),expires_at,granted_by,granted_at
FROM organization_capability_grant
WHERE revoked_at IS NULL AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP(6));
