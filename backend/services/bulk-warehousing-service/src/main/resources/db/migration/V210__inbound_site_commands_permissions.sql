-- 现场动作分别授权，沿用已声明的车次数据范围。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description) VALUES
 (UUID_TO_BIN('019a65d0-0210-7000-8000-000000000001'),'TENANT','warehousing.inboundtask:raise_site_gate','warehousing.inboundtask','raise_site_gate','入库现场抬杆','Raise inbound gate','提交受控的车次现场设备命令'),
 (UUID_TO_BIN('019a65d0-0210-7000-8000-000000000002'),'TENANT','warehousing.inboundtask:call_site_number','warehousing.inboundtask','call_site_number','入库现场叫号','Call inbound number','提交受控的车次现场设备命令'),
 (UUID_TO_BIN('019a65d0-0210-7000-8000-000000000003'),'TENANT','warehousing.inboundtask:print_site_ticket','warehousing.inboundtask','print_site_ticket','打印入库作业票','Print inbound task slip','提交受控的车次现场设备命令');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
SELECT id,'ALL',resource FROM access_permission WHERE code IN ('warehousing.inboundtask:raise_site_gate','warehousing.inboundtask:call_site_number','warehousing.inboundtask:print_site_ticket');
-- 沿用已经具备全范围车次登记的系统租户管理员及原授权人。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,created_by,created_at,updated_by,updated_at)
SELECT r.id,p.id,'TENANT',prior.granted_by,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role r JOIN access_role_permission prior ON prior.role_id=r.id
JOIN access_permission prior_permission ON prior_permission.id=prior.permission_id AND prior_permission.code='warehousing.inboundtask:create_from_notice'
JOIN access_role_data_policy d ON d.role_id=r.id AND d.permission_id=prior.permission_id AND d.scope_type='ALL'
CROSS JOIN access_permission p WHERE p.code IN ('warehousing.inboundtask:raise_site_gate','warehousing.inboundtask:call_site_number','warehousing.inboundtask:print_site_ticket')
 AND r.scope_type='TENANT' AND r.role_type='SYSTEM' AND r.code='sys_tenant_admin' AND r.status='ENABLED' AND r.deleted_at IS NULL;
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,created_by,created_at,updated_by,updated_at)
SELECT rp.role_id,rp.permission_id,'ALL',p.resource,NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id WHERE p.code IN ('warehousing.inboundtask:raise_site_gate','warehousing.inboundtask:call_site_number','warehousing.inboundtask:print_site_ticket');
UPDATE access_role r JOIN access_role_permission rp ON rp.role_id=r.id
 AND rp.permission_id=UUID_TO_BIN('019a65d0-0210-7000-8000-000000000001')
 SET r.version=r.version+1,r.updated_by=NULL,r.updated_at=CURRENT_TIMESTAMP(6);
UPDATE tenant_member m JOIN access_member_authorization a ON a.tenant_id=m.tenant_id AND a.member_id=m.id
 JOIN access_role_permission rp ON rp.role_id=a.role_id AND rp.permission_id=UUID_TO_BIN('019a65d0-0210-7000-8000-000000000001')
 SET m.authority_version=m.authority_version+1
 WHERE a.inherit_data_scope=TRUE AND (a.expires_at IS NULL OR a.expires_at>CURRENT_TIMESTAMP(6));
