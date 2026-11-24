-- 平台附件配置的独立权限。通过统一角色管理授权，租户角色不能使用。
INSERT INTO access_permission(id, scope_type, code, resource, action, name_zh, name_en, description)
VALUES
  (UUID_TO_BIN('019a65d0-0196-7000-8000-000000000001'), 'PLATFORM',
   'configuration.attachmentpolicy:read', 'configuration.attachmentpolicy', 'read',
   '查看附件上传策略', 'Read attachment upload policies', '查看平台级附件格式、启停及大小限制。'),
  (UUID_TO_BIN('019a65d0-0196-7000-8000-000000000002'), 'PLATFORM',
   'configuration.attachmentpolicy:update', 'configuration.attachmentpolicy', 'update',
   '修改附件上传策略', 'Update attachment upload policies', '修改附件类型的启停和单文件大小限制。');
