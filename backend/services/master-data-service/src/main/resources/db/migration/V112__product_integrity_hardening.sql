-- 显式IS NOT NULL避免MySQL CHECK的UNKNOWN放过数值精度空值；服务端与数据库双层约束。
ALTER TABLE md_product_attribute_revision
 ADD CONSTRAINT ck_par_numeric_scale_complete CHECK(
 (data_type='INTEGER' AND numeric_scale IS NOT NULL AND numeric_scale=0)
 OR (data_type='DECIMAL' AND numeric_scale IS NOT NULL AND numeric_scale BETWEEN 0 AND 12)
 OR (data_type NOT IN ('INTEGER','DECIMAL') AND numeric_scale IS NULL AND unit_id IS NULL));
ALTER TABLE md_product_v1
 ADD CONSTRAINT ck_product_enabled_structure CHECK(status<>'ENABLED' OR structure_state IN ('COMPLETE','LEGACY_PENDING')),
 ADD CONSTRAINT ck_product_archive_draft CHECK(draft_archived_at IS NULL OR status='DRAFT');
-- 对账与动态查询的租户前导索引，不新增所有低基数字段排列。
CREATE INDEX ix_product_status_code ON md_product_v1(tenant_id,status,code,id);
CREATE INDEX ix_product_category ON md_product_v1(tenant_id,category_id,status,id);
CREATE INDEX ix_product_decimal ON md_product_v1_attribute_value(tenant_id,template_item_id,decimal_value,product_id);
CREATE INDEX ix_product_integer ON md_product_v1_attribute_value(tenant_id,template_item_id,integer_value,product_id);
CREATE INDEX ix_product_date ON md_product_v1_attribute_value(tenant_id,template_item_id,date_value,product_id);
