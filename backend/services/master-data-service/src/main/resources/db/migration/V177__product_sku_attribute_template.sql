-- 类目默认普通货号模板供 SPU 确认；未确认的商品无生效绑定。
ALTER TABLE md_product_v1
 ADD COLUMN sku_attribute_template_version_id BINARY(16) NULL COMMENT 'SPU 确认的普通货号模板版本UUID；NULL表示未确认',
 ADD CONSTRAINT fk_product_sku_attribute_template FOREIGN KEY (tenant_id,sku_attribute_template_version_id)
 REFERENCES md_product_template_version (tenant_id,id);
