-- 附件读写按可信租户执行，平台台账需要独立读取权限。
INSERT INTO access_permission(id, scope_type, code, resource, action, name_zh, name_en, description)
VALUES
  (UUID_TO_BIN('019a65d0-0198-7000-8000-000000000001'), 'TENANT',
   'configuration.attachment:read', 'configuration.attachment', 'read',
   '查看本人附件', 'Read own attachments', '按业务标识查看当前租户本人上传的附件资料。'),
  (UUID_TO_BIN('019a65d0-0198-7000-8000-000000000002'), 'TENANT',
   'configuration.attachment:upload', 'configuration.attachment', 'upload',
   '上传附件', 'Upload attachments', '在当前可信租户下上传附件。'),
  (UUID_TO_BIN('019a65d0-0198-7000-8000-000000000003'), 'PLATFORM',
   'configuration.attachmentledger:read', 'configuration.attachmentledger', 'read',
   '查看附件台账', 'Read attachment ledger', '查看跨租户的附件资料台账。');

-- 默认导航引用统一权限，由管理员通过公共授权系统配置角色。
INSERT INTO platform_menu(id, parent_id, type, code, title_zh, title_en, route_path,
                          component_key, icon, permission_code, sort_order, scope_type)
VALUES
  (UUID_TO_BIN('019a65d0-0198-7000-8000-000000000004'), UUID_TO_BIN('019a65d0-0027-7000-8000-000000000001'),
   'MENU', 'builtin.navigation.platform.attachment-policies', '附件配置', 'Attachment policies',
   '/platform/attachment-policies', 'platform/attachment-policies', 'ri/settings-3-line',
   'configuration.attachmentpolicy:read', 25, 'PLATFORM'),
  (UUID_TO_BIN('019a65d0-0198-7000-8000-000000000005'), UUID_TO_BIN('019a65d0-0027-7000-8000-000000000001'),
   'MENU', 'builtin.navigation.platform.attachments', '附件管理', 'Attachments',
   '/platform/attachments', 'platform/attachments', 'ri/attachment-line',
   'configuration.attachmentledger:read', 26, 'PLATFORM');
