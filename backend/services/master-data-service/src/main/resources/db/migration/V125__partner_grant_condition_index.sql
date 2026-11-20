-- 客商名称等值/集合授权条件与租户共同定位；编码及组织维度沿用既有索引。
CREATE INDEX ix_partner_tenant_name ON md_partner(tenant_id,name,id);
