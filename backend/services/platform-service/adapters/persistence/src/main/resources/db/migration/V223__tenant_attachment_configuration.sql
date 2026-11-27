-- 租户独立维护的上传规则。无租户行时读取已冻结的原平台规则，首次修改以版本 1 建立独立规则。
CREATE TABLE tenant_attachment_upload_policy (
    id BINARY(16) PRIMARY KEY COMMENT '租户规则记录 UUID',
    tenant_id BINARY(16) NOT NULL COMMENT '可信租户 UUID',
    type_key VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定文件类型键',
    enabled BOOLEAN NOT NULL COMMENT '本租户是否允许新上传',
    max_bytes BIGINT NOT NULL CHECK (max_bytes > 0 AND max_bytes <= 20971520) COMMENT '单文件上限，单位字节',
    version BIGINT NOT NULL CHECK (version > 0) COMMENT '乐观锁版本，首次修改为 1',
    created_by BINARY(16) NULL COMMENT '创建账号，系统无账号时为空',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 创建时间',
    updated_by BINARY(16) NULL COMMENT '最近修改账号，系统无账号时为空',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库生成的 UTC 最近修改时间',
    UNIQUE KEY uk_tenant_attachment_type (tenant_id, type_key)
) COMMENT '本租户独立的附件上传规则';

-- 新旧权限代码分离，已有平台角色不获得租户访问权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES
 (UUID_TO_BIN('019a65d0-0223-7000-8000-000000000001'),'TENANT','configuration.tenantattachmentpolicy:read','configuration.tenantattachmentpolicy','read','查看附件配置','Read attachment settings','查看本租户的附件上传规则。'),
 (UUID_TO_BIN('019a65d0-0223-7000-8000-000000000002'),'TENANT','configuration.tenantattachmentpolicy:update','configuration.tenantattachmentpolicy','update','修改附件配置','Update attachment settings','独立调整本租户上传格式和单文件上限。'),
 (UUID_TO_BIN('019a65d0-0223-7000-8000-000000000003'),'TENANT','configuration.attachment:ledger','configuration.attachment','ledger','查看附件台账','Read attachment ledger','查看和下载本租户的附件。');

-- 保留旧授权历史，停用原平台操作权限。
UPDATE access_permission SET enabled=FALSE, updated_by=NULL, updated_at=CURRENT_TIMESTAMP(6)
WHERE scope_type='PLATFORM'
  AND code IN ('configuration.attachmentpolicy:read','configuration.attachmentpolicy:update','configuration.attachmentledger:read');

-- 平台入口停用，租户系统配置使用独立节点，避免继承原平台菜单发布关系。
UPDATE platform_menu SET enabled=FALSE, visible=FALSE, version=version+1, updated_by=NULL
WHERE code IN ('builtin.navigation.platform.attachment-policies','builtin.navigation.platform.attachments');

INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,permission_code,sort_order,scope_type)
VALUES
 (UUID_TO_BIN('019a65d0-0223-7000-8000-000000000004'),UUID_TO_BIN('019a65d0-0027-7000-8000-000000000004'),'MENU','builtin.navigation.configuration.attachment-policies','附件配置','Attachment settings','/configuration/attachment-policies','configuration/attachment-policies','ri/settings-3-line','configuration.tenantattachmentpolicy:read',25,'TENANT'),
 (UUID_TO_BIN('019a65d0-0223-7000-8000-000000000005'),UUID_TO_BIN('019a65d0-0027-7000-8000-000000000004'),'MENU','builtin.navigation.configuration.attachments','附件管理','Attachments','/configuration/attachments','configuration/attachments','ri/attachment-line','configuration.attachment:ledger',26,'TENANT');

INSERT INTO platform_menu_resource(menu_id,resource_code)
VALUES
 (UUID_TO_BIN('019a65d0-0223-7000-8000-000000000004'),'configuration.tenantattachmentpolicy'),
 (UUID_TO_BIN('019a65d0-0223-7000-8000-000000000005'),'configuration.attachment');

-- 仅补齐现有系统租户管理员的能力，沿用原角色授权来源与租户，不迁移平台授权。
INSERT IGNORE INTO access_role_permission(role_id,permission_id,scope_type,granted_by,tenant_id)
SELECT r.id,p.id,'TENANT',prior.granted_by,r.tenant_id
FROM access_role r JOIN (SELECT role_id,MIN(granted_by) granted_by FROM access_role_permission GROUP BY role_id) prior ON prior.role_id=r.id
JOIN access_permission p ON p.code IN ('configuration.tenantattachmentpolicy:read','configuration.tenantattachmentpolicy:update','configuration.attachment:ledger','configuration.attachment:read','configuration.attachment:upload')
WHERE r.system_key LIKE 'TENANT_ADMIN:%' AND r.deleted_at IS NULL;

INSERT IGNORE INTO access_member_authorization_permission(tenant_id,member_id,role_id,permission_id)
SELECT g.tenant_id,g.member_id,g.role_id,p.id
FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id AND r.tenant_id=g.tenant_id
JOIN access_permission p ON p.code IN ('configuration.tenantattachmentpolicy:read','configuration.tenantattachmentpolicy:update','configuration.attachment:ledger','configuration.attachment:read','configuration.attachment:upload')
WHERE r.system_key LIKE 'TENANT_ADMIN:%' AND r.deleted_at IS NULL;

UPDATE tenant_member m SET authority_version=authority_version+1, updated_by=NULL, updated_at=CURRENT_TIMESTAMP(6)
WHERE EXISTS (SELECT 1 FROM access_member_authorization g JOIN access_role r ON r.id=g.role_id
              WHERE g.tenant_id=m.tenant_id AND g.member_id=m.id AND r.system_key LIKE 'TENANT_ADMIN:%' AND r.deleted_at IS NULL);
