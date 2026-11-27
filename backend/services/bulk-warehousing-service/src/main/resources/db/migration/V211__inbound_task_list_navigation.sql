-- 全生命周期作业台账使用独立查询权限，原队列权限的可读范围保持原定义。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES (UUID_TO_BIN('019a65d0-0211-7000-8000-000000000001'),'TENANT',
    'warehousing.inboundtask:read_task_list','warehousing.inboundtask','read_task_list',
    '查询入库作业单列表','Read inbound task list','读取当前租户的作业台账及历史记录');

-- 本期仅支持显式授权的租户全范围。通过统一授权中心分配列表权限及数据策略。
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission WHERE code='warehousing.inboundtask:read_task_list';

-- 仅向已拥有作业队列全范围的系统租户管理员初始化台账权限；其他角色由授权中心单独配置。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,created_by,created_at,updated_by,updated_at)
SELECT r.id,p.id,'TENANT',prior.granted_by,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role r
JOIN access_role_permission prior ON prior.role_id=r.id
JOIN access_permission q ON q.id=prior.permission_id AND q.code='warehousing.inboundtask:read_workbench_queue'
JOIN access_role_data_policy d ON d.role_id=r.id AND d.permission_id=q.id AND d.scope_type='ALL'
JOIN access_permission p ON p.code='warehousing.inboundtask:read_task_list'
WHERE r.scope_type='TENANT' AND r.role_type='SYSTEM' AND r.code='sys_tenant_admin'
    AND r.status='ENABLED' AND r.deleted_at IS NULL;

-- 同步初始化数据策略和版本，使授权缓存按统一机制重新求值。
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,created_by,created_at,updated_by,updated_at)
SELECT rp.role_id,p.id,'ALL',p.resource,NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id
WHERE p.code='warehousing.inboundtask:read_task_list';

UPDATE access_role r JOIN access_role_permission rp ON rp.role_id=r.id
    AND rp.permission_id=UUID_TO_BIN('019a65d0-0211-7000-8000-000000000001')
SET r.version=r.version+1,r.updated_by=NULL,r.updated_at=CURRENT_TIMESTAMP(6);

UPDATE tenant_member m JOIN access_member_authorization a ON a.tenant_id=m.tenant_id AND a.member_id=m.id
JOIN access_role_permission rp ON rp.role_id=a.role_id
    AND rp.permission_id=UUID_TO_BIN('019a65d0-0211-7000-8000-000000000001')
SET m.authority_version=m.authority_version+1
WHERE a.expires_at IS NULL OR a.expires_at>CURRENT_TIMESTAMP(6);

-- 菜单与列表使用同一权限，详情仍执行自身的对象授权。
INSERT INTO platform_menu(id,parent_id,type,code,title_zh,title_en,route_path,component_key,icon,
    permission_code,sort_order,scope_type,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN('019a65d0-0211-7000-8000-000000000002'),id,'MENU',
    'builtin.navigation.warehousing.inbound-tasks','入库作业单','Inbound tasks',
    '/warehousing/inbound-tasks','warehousing/inbound-tasks','ri/file-list-line',
    'warehousing.inboundtask:read_task_list',3,'TENANT',NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM platform_menu WHERE code='builtin.navigation.warehousing.inbound' AND scope_type='TENANT' AND type='DIRECTORY';
