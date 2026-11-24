-- 同一通知固定首次确认的已发布模板版本。历史通知留空，登记时核对历史车次的一致性。
ALTER TABLE bw_inbound_notice
 ADD COLUMN execution_template_id BINARY(16) NULL COMMENT '本通知固定的入库品类模板版本 UUID，未绑定时为空',
 ADD CONSTRAINT fk_bw_notice_execution_template FOREIGN KEY (tenant_id,execution_template_id)
 REFERENCES bw_commodity_template(tenant_id,id);

-- 独立的到场登记权限。原通知读权限由服务端同时复核。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0207-7000-8000-000000000001'),'TENANT','warehousing.inboundtask:create_from_notice',
 'warehousing.inboundtask','create_from_notice','从入仓通知登记车次','Register arrival from notice','在可读的已签发通知下登记车次');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
VALUES(UUID_TO_BIN('019a65d0-0207-7000-8000-000000000001'),'ALL','warehousing.inboundtask');

-- 给已有通知全范围编辑的系统租户管理员接通入口，保留定制角色和成员范围。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,created_by,created_at,updated_by,updated_at)
SELECT p.role_id,UUID_TO_BIN('019a65d0-0207-7000-8000-000000000001'),'TENANT',p.granted_by,
 CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission p JOIN access_permission old ON old.id=p.permission_id AND old.code='warehousing.inbound:update'
JOIN access_role r ON r.id=p.role_id AND r.scope_type='TENANT' AND r.code='sys_tenant_admin'
 AND r.role_type='SYSTEM' AND r.status='ENABLED' AND r.deleted_at IS NULL
JOIN access_role_data_policy d ON d.role_id=p.role_id AND d.permission_id=p.permission_id AND d.scope_type='ALL';
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,created_by,created_at,updated_by,updated_at)
SELECT role_id,permission_id,'ALL','warehousing.inboundtask',NULL,CURRENT_TIMESTAMP(6),
 NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission WHERE permission_id=UUID_TO_BIN('019a65d0-0207-7000-8000-000000000001');
UPDATE access_role r JOIN access_role_permission p ON p.role_id=r.id
 AND p.permission_id=UUID_TO_BIN('019a65d0-0207-7000-8000-000000000001')
 SET r.version=r.version+1,r.updated_by=NULL,r.updated_at=CURRENT_TIMESTAMP(6);
UPDATE tenant_member m JOIN access_member_authorization a ON a.tenant_id=m.tenant_id AND a.member_id=m.id
 JOIN access_role_permission p ON p.role_id=a.role_id AND p.permission_id=UUID_TO_BIN('019a65d0-0207-7000-8000-000000000001')
 SET m.authority_version=m.authority_version+1
 WHERE a.inherit_data_scope=TRUE AND (a.expires_at IS NULL OR a.expires_at>CURRENT_TIMESTAMP(6));
