-- 当前只表达成员的主要组织归属；不代替岗位、任职、多组织关系或组织树模型。
CREATE TABLE tenant_member_primary_organization (
 tenant_id BINARY(16) NOT NULL,
 member_id BINARY(16) NOT NULL,
 organization_id BINARY(16) NOT NULL,
 PRIMARY KEY(tenant_id,member_id),
 INDEX ix_primary_organization(tenant_id,organization_id)
);
