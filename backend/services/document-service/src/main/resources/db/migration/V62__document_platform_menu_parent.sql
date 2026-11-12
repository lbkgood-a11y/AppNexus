-- 修复首批单据目录入口：置于既有平台管理目录，保留权限、路由、启停及租户授权。
UPDATE platform_menu
 SET parent_id=UUID_TO_BIN('019a65d0-0027-7000-8000-000000000001'),
     sort_order=15,version=version+1
 WHERE code='builtin.navigation.platform.documents' AND scope_type='PLATFORM';
