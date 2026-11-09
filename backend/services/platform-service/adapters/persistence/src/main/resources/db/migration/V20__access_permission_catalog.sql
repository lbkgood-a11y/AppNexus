-- V17 的组织专用权限目录已发布，先解除编码外键并保留成员授权关系，再演进为统一权限目录。
ALTER TABLE access_member_permission DROP FOREIGN KEY fk_access_member_permission_catalog;
DROP TABLE access_permission;

-- 独立权限目录是业务授权的唯一资源权威；菜单仅引用权限，不保存另一套角色授权关系。
CREATE TABLE access_permission (
 id BINARY(16) NOT NULL,
 scope_type VARCHAR(16) NOT NULL,
 code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 resource VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name_zh VARCHAR(100) NOT NULL,
 name_en VARCHAR(100) NOT NULL,
 description VARCHAR(500) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 system_managed BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 CONSTRAINT uk_access_permission_code UNIQUE(code),
 CONSTRAINT uk_access_permission_resource_action UNIQUE(scope_type,resource,action),
 CONSTRAINT ck_access_permission_scope CHECK(scope_type IN('PLATFORM','TENANT')),
 CONSTRAINT ck_access_permission_code CHECK(REGEXP_LIKE(code,'^[a-z][a-z0-9.]*:[a-z][a-z0-9_]*$','c')),
 CONSTRAINT ck_access_permission_resource CHECK(REGEXP_LIKE(resource,'^[a-z][a-z0-9.]*$','c')),
 CONSTRAINT ck_access_permission_action CHECK(REGEXP_LIKE(action,'^[a-z][a-z0-9_]*$','c')),
 CONSTRAINT ck_access_permission_version CHECK(version>=0),
 INDEX ix_access_permission_scope_enabled(scope_type,enabled,resource,action)
);

-- 首批权限覆盖当前已接通的账号、租户、角色、菜单和组织能力；UUID 固定以保证各环境引用一致。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000001'),'PLATFORM','identity.account:read','identity.account','read','查看账号','Read accounts','查看平台账号最小必要资料，不包含凭据。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000002'),'PLATFORM','identity.account:create','identity.account','create','创建账号','Create accounts','创建平台账号；密码及凭据仍由独立安全流程处理。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000003'),'PLATFORM','identity.account:update','identity.account','update','修改账号','Update accounts','修改账号基础资料和启停状态。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000004'),'PLATFORM','tenant:read','tenant','read','查看租户','Read tenants','查看平台租户及其状态。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000005'),'PLATFORM','tenant:create','tenant','create','创建租户','Create tenants','创建租户并执行受控初始化。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000006'),'PLATFORM','tenant:update','tenant','update','修改租户','Update tenants','修改租户基础资料和启停状态。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000007'),'PLATFORM','tenant.member:read','tenant.member','read','查看租户成员','Read tenant members','平台代管时查看目标租户成员摘要。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000008'),'PLATFORM','access.role:read','access.role','read','查看平台角色','Read platform roles','查看平台管理域角色。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000009'),'PLATFORM','access.role:create','access.role','create','创建平台角色','Create platform roles','创建平台管理域自定义角色。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000000a'),'PLATFORM','access.role:update','access.role','update','修改平台角色','Update platform roles','修改平台角色资料。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000000b'),'PLATFORM','access.role:enable','access.role','enable','启停平台角色','Enable platform roles','启用或停用平台角色并触发授权版本变化。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000000c'),'PLATFORM','access.role:delete','access.role','delete','删除平台角色','Delete platform roles','逻辑删除无引用的已停用平台角色。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000000d'),'PLATFORM','access.role:grant','access.role','grant','配置平台角色权限','Grant platform role permissions','覆盖保存平台角色的功能权限与数据策略。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000000e'),'PLATFORM','configuration.menu:read','configuration.menu','read','查看菜单配置','Read menu configuration','查看平台菜单定义，不代表可访问对应业务。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000000f'),'PLATFORM','configuration.menu:create','configuration.menu','create','创建菜单配置','Create menu configuration','创建受控菜单定义。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000010'),'PLATFORM','configuration.menu:update','configuration.menu','update','修改菜单配置','Update menu configuration','修改允许编辑的菜单元数据。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000011'),'PLATFORM','configuration.menu:enable','configuration.menu','enable','启停菜单配置','Enable menu configuration','启用或停用前端菜单入口，不自动关闭后端业务。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000012'),'PLATFORM','configuration.menu:delete','configuration.menu','delete','删除菜单配置','Delete menu configuration','删除无引用且允许删除的菜单定义。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000013'),'PLATFORM','configuration.menu:publish','configuration.menu','publish','发布系统菜单','Publish system menus','通过受控发布流程维护系统菜单。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000014'),'TENANT','access.tenant.role:read','access.tenant.role','read','查看租户角色','Read tenant roles','查看当前租户角色。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000015'),'TENANT','access.tenant.role:create','access.tenant.role','create','创建租户角色','Create tenant roles','创建当前租户自定义角色。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000016'),'TENANT','access.tenant.role:update','access.tenant.role','update','修改租户角色','Update tenant roles','修改当前租户角色资料和状态。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000017'),'TENANT','access.tenant.role:delete','access.tenant.role','delete','删除租户角色','Delete tenant roles','逻辑删除当前租户无引用的已停用角色。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000018'),'TENANT','access.tenant.role:grant','access.tenant.role','grant','配置租户角色权限','Grant tenant role permissions','覆盖保存当前租户角色的功能权限与数据策略。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-000000000019'),'TENANT','organization.unit:read','organization.unit','read','查看组织','Read organization units','按数据范围查看当前租户组织结构。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000001a'),'TENANT','organization.unit:create','organization.unit','create','创建组织','Create organization units','在允许的数据范围内创建组织单元。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000001b'),'TENANT','organization.unit:update','organization.unit','update','修改组织','Update organization units','在允许的数据范围内修改组织单元。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000001c'),'TENANT','organization.unit:delete','organization.unit','delete','删除组织','Delete organization units','在允许的数据范围内删除符合领域约束的组织单元。'),
(UUID_TO_BIN('019a65d0-0000-7000-8000-00000000001d'),'TENANT','organization.permission:grant','organization.permission','grant','管理组织授权','Grant organization permissions','管理当前租户组织能力授权；该能力不可由普通组织写权限推导。');

-- 旧成员直授权继续承担迁移期双读来源；只重编码到统一权限码，不扩大权限或修改有效期。
UPDATE access_member_permission SET permission_code=CASE permission_code
 WHEN 'organization.read' THEN 'organization.unit:read'
 WHEN 'organization.create' THEN 'organization.unit:create'
 WHEN 'organization.update' THEN 'organization.unit:update'
 WHEN 'organization.delete' THEN 'organization.unit:delete'
 WHEN 'organization.grant' THEN 'organization.permission:grant'
 ELSE permission_code END;
ALTER TABLE access_member_permission MODIFY permission_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
ALTER TABLE access_member_permission ADD CONSTRAINT fk_access_member_permission_catalog
 FOREIGN KEY(permission_code) REFERENCES access_permission(code);
