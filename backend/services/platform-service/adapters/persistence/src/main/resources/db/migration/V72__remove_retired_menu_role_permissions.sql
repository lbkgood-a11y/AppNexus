-- 仅清理已经停用的菜单启停/发布历史角色关联；保留权限定义及历史审计用于追溯。
-- 推进受影响角色版本，防止旧授权草稿覆盖迁移结果。
UPDATE access_role r SET r.version=r.version+1
WHERE EXISTS (SELECT 1 FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id
 WHERE rp.role_id=r.id AND p.scope_type='PLATFORM' AND p.enabled=FALSE
 AND p.code IN('configuration.menu:enable','configuration.menu:publish'));
DELETE rp FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id
WHERE p.scope_type='PLATFORM' AND p.enabled=FALSE AND p.code IN('configuration.menu:enable','configuration.menu:publish');
