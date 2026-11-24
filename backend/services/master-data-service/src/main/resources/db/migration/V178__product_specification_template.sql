-- 商品确认规格轴版本；自有规格保存采用版本及值，不重写旧规格。
ALTER TABLE md_product_v1
 ADD COLUMN specification_template_version_id BINARY(16) NULL COMMENT 'SPU 确认的规格轴模板版本UUID；NULL表示尚未确认',
 ADD CONSTRAINT fk_product_specification_template FOREIGN KEY (tenant_id,specification_template_version_id)
 REFERENCES md_product_template_version (tenant_id,id);
ALTER TABLE md_product_specification
 ADD COLUMN template_version_id BINARY(16) NULL COMMENT '新规格采用的 SPU 规格轴模板版本UUID',
 ADD COLUMN template_values JSON NULL COMMENT '规格轴值及固定字段快照；旧版记录使用原属性明细',
 ADD CONSTRAINT fk_product_specification_bound_template FOREIGN KEY (tenant_id,template_version_id)
 REFERENCES md_product_template_version (tenant_id,id);
