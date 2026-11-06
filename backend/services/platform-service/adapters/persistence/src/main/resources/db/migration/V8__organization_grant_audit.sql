-- 授权生命周期审计与授权变更处于同一 MySQL 事务；不保存凭据或请求正文。
CREATE TABLE organization_grant_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 target_member_id BINARY(16) NOT NULL,
 actor_member_id BINARY(16) NOT NULL,
 capability VARCHAR(20) NOT NULL,
 action VARCHAR(20) NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX ix_org_grant_audit_tenant_time (tenant_id,occurred_at,id),
 CONSTRAINT ck_org_grant_action CHECK (action IN ('GRANTED','REVOKED'))
);
