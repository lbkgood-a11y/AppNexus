-- 普通货号属性首次保存时固定模板版本。现有货号保留空值，编辑时读取所属商品的固定类目配置。
ALTER TABLE md_product_sku
 ADD COLUMN attribute_template_version_id BINARY(16) NULL COMMENT '普通货号属性的固定模板版本UUID；NULL表示尚未采用',
 ADD COLUMN attribute_values JSON NULL COMMENT '普通货号属性值快照，按固定模板项保存；NULL表示尚未保存',
 ADD CONSTRAINT fk_sku_attribute_template_version FOREIGN KEY (tenant_id,attribute_template_version_id)
 REFERENCES md_product_template_version (tenant_id,id);
