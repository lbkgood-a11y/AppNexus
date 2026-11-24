-- 草稿可先存在，计量完成后回填原作业的计量凭证。
ALTER TABLE bw_receipt_order MODIFY metering_id BINARY(16) NULL
 COMMENT '采用的同作业计量单 UUID，要素收集阶段为空';

-- 打开草稿的权限与过账权限分别授权。
INSERT INTO access_permission(id,scope_type,code,resource,action,name_zh,name_en,description)
VALUES(UUID_TO_BIN('019a65d0-0208-7000-8000-000000000001'),'TENANT','warehousing.receipt:open_draft',
 'warehousing.receipt','open_draft','打开入库单草稿','Open receipt draft','为到场作业打开入库单以收集执行要素');
INSERT INTO access_permission_scope_type(permission_id,scope_type,resource_type)
VALUES(UUID_TO_BIN('019a65d0-0208-7000-8000-000000000001'),'ALL','warehousing.receipt');

-- 沿用已具备入库草稿全范围的系统租户管理员，普通角色仍由授权中心配置。
INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by,granted_at,created_by,created_at,updated_by,updated_at)
SELECT r.id,UUID_TO_BIN('019a65d0-0208-7000-8000-000000000001'),'TENANT',NULL,
 CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role r WHERE r.scope_type='TENANT' AND r.role_type='SYSTEM' AND r.code='sys_tenant_admin'
 AND r.status='ENABLED' AND r.deleted_at IS NULL AND EXISTS(
 SELECT 1 FROM access_role_permission rp JOIN access_permission p ON p.id=rp.permission_id
 JOIN access_role_data_policy d ON d.role_id=rp.role_id AND d.permission_id=rp.permission_id AND d.scope_type='ALL'
 WHERE rp.role_id=r.id AND p.code IN ('warehousing.receipt:create_bulk_draft','warehousing.receipt:create_steel_draft'));
INSERT INTO access_role_data_policy(role_id,permission_id,scope_type,resource_type,configured_by,configured_at,created_by,created_at,updated_by,updated_at)
SELECT role_id,permission_id,'ALL','warehousing.receipt',NULL,CURRENT_TIMESTAMP(6),
 NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6)
FROM access_role_permission WHERE permission_id=UUID_TO_BIN('019a65d0-0208-7000-8000-000000000001');
UPDATE access_role r JOIN access_role_permission p ON p.role_id=r.id
 AND p.permission_id=UUID_TO_BIN('019a65d0-0208-7000-8000-000000000001')
 SET r.version=r.version+1,r.updated_by=NULL,r.updated_at=CURRENT_TIMESTAMP(6);
UPDATE tenant_member m JOIN access_member_authorization a ON a.tenant_id=m.tenant_id AND a.member_id=m.id
 JOIN access_role_permission p ON p.role_id=a.role_id AND p.permission_id=UUID_TO_BIN('019a65d0-0208-7000-8000-000000000001')
 SET m.authority_version=m.authority_version+1
 WHERE a.inherit_data_scope=TRUE AND (a.expires_at IS NULL OR a.expires_at>CURRENT_TIMESTAMP(6));
