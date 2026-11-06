-- GRANT 仅可由受控运维建立，不向普通授权接口开放委派。
ALTER TABLE organization_capability_grant DROP CHECK ck_org_capability;
ALTER TABLE organization_capability_grant ADD CONSTRAINT ck_org_capability CHECK (capability IN ('READ','CREATE','UPDATE','DELETE','GRANT'));
